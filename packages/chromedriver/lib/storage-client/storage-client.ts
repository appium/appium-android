import {createHash, type Hash} from 'node:crypto';
import path from 'node:path';

import {system, fs, logger, tempDir, zip, util} from '@appium/support';
import {asyncmap} from 'asyncbox';
import * as semver from 'semver';

import {STORAGE_REQ_TIMEOUT_MS, GOOGLEAPIS_CDN, USER_AGENT, CHROMELABS_URL, ARCH, OS, CPU} from '../constants.js';
import type {ChromedriverStorageClientOpts, SyncOptions, OSInfo, ChromedriverDetailsMapping} from '../types.js';
import {getChromedriverDir, retrieveData, getOsInfo, convertToInt, getCpuType, compareVersions} from '../utils.js';
import {parseKnownGoodVersionsWithDownloadsJson, parseLatestKnownGoodVersionsJson} from './chromelabs.js';
import {parseGoogleapiStorageXml} from './googleapis.js';

const MAX_PARALLEL_DOWNLOADS = 5;

interface StorageInfo {
  url: string;
  accept: string;
}

const STORAGE_INFOS: readonly StorageInfo[] = [
  {
    url: GOOGLEAPIS_CDN,
    accept: 'application/xml',
  },
  {
    url: `${CHROMELABS_URL}/chrome-for-testing/known-good-versions-with-downloads.json`,
    accept: 'application/json',
  },
];

const CHROME_FOR_TESTING_LAST_GOOD_VERSIONS = `${CHROMELABS_URL}/chrome-for-testing/last-known-good-versions.json`;

const log = logger.getLogger('ChromedriverStorageClient');

export class ChromedriverStorageClient {
  readonly chromedriverDir: string;
  readonly timeout: number;
  private mapping: ChromedriverDetailsMapping;

  constructor(args: ChromedriverStorageClientOpts = {}) {
    const {chromedriverDir = getChromedriverDir(), timeout = STORAGE_REQ_TIMEOUT_MS} = args;
    this.chromedriverDir = chromedriverDir;
    this.timeout = timeout;
    this.mapping = {};
  }

  /**
   * Retrieves chromedriver mapping from the storage
   *
   * @param shouldParseNotes [true] - if set to `true`
   * then additional chromedrivers info is going to be retrieved and
   * parsed from release notes
   * @returns Promise<ChromedriverDetailsMapping>
   */
  async retrieveMapping(shouldParseNotes = true): Promise<ChromedriverDetailsMapping> {
    const retrieveResponseSafely = async ({url, accept}: StorageInfo): Promise<string | undefined> => {
      try {
        return await retrieveData(
          url,
          {
            'user-agent': USER_AGENT,
            accept: `${accept}, */*`,
          },
          {timeout: this.timeout},
        );
      } catch (e) {
        const err = e as Error;
        log.debug(err.stack);
        log.warn(
          `Cannot retrieve Chromedrivers info from ${url}. ` +
            `Make sure this URL is accessible from your network. ` +
            `Original error: ${err.message}`,
        );
      }
    };
    const [xmlStr, jsonStr] = await Promise.all(STORAGE_INFOS.map(retrieveResponseSafely));
    // Apply the best effort approach and fetch the mapping from at least one server if possible.
    // We'll fail later anyway if the target chromedriver version is not there.
    if (!xmlStr && !jsonStr) {
      throw new Error(
        `Cannot retrieve the information about available Chromedrivers from ` +
          `${STORAGE_INFOS.map(({url}) => url).join(', ')}. Please make sure these URLs are available ` +
          `within your local network, check Appium server logs and/or ` +
          `consult the driver troubleshooting guide.`,
      );
    }
    this.mapping = xmlStr ? await parseGoogleapiStorageXml(xmlStr, shouldParseNotes) : {};
    if (jsonStr) {
      Object.assign(this.mapping, parseKnownGoodVersionsWithDownloadsJson(jsonStr));
    }
    return this.mapping;
  }

  /**
   * Retrieves chromedrivers from the remote storage to the local file system
   *
   * @param opts - Synchronization options (versions, minBrowserVersion, osInfo)
   * @throws {Error} if there was a problem while retrieving the drivers
   * @returns The list of successfully synchronized driver keys
   */
  async syncDrivers(opts: SyncOptions = {}): Promise<string[]> {
    if (util.isEmpty(this.mapping)) {
      await this.retrieveMapping(!!opts.minBrowserVersion);
    }
    if (util.isEmpty(this.mapping)) {
      throw new Error('Cannot retrieve chromedrivers mapping from Google storage');
    }

    const driversToSync = this.selectMatchingDrivers(opts.osInfo ?? (await getOsInfo()), opts);
    if (util.isEmpty(driversToSync)) {
      log.debug(`There are no drivers to sync. Exiting`);
      return [];
    }
    log.debug(
      `Got ${util.pluralize('driver', driversToSync.length, true)} to sync: ` + JSON.stringify(driversToSync, null, 2),
    );

    const synchronizedDrivers: string[] = [];
    await asyncmap(
      driversToSync,
      async (driverKey) => {
        if (await this.retrieveDriver(driverKey, !util.isEmpty(opts))) {
          synchronizedDrivers.push(driverKey);
        }
      },
      {concurrency: MAX_PARALLEL_DOWNLOADS},
    );
    if (!util.isEmpty(synchronizedDrivers)) {
      log.info(`Successfully synchronized ` + `${util.pluralize('chromedriver', synchronizedDrivers.length, true)}`);
    } else {
      log.info(`No chromedrivers were synchronized`);
    }
    return synchronizedDrivers;
  }

  /**
   * Returns the latest chromedriver version for Chrome for Testing
   *
   * @returns The latest stable chromedriver version string
   * @throws {Error} if the version cannot be fetched from the remote API
   */
  async getLatestKnownGoodVersion(): Promise<string> {
    let jsonStr: string;
    try {
      jsonStr = await retrieveData(
        CHROME_FOR_TESTING_LAST_GOOD_VERSIONS,
        {
          'user-agent': USER_AGENT,
          accept: `application/json, */*`,
        },
        {timeout: STORAGE_REQ_TIMEOUT_MS},
      );
    } catch (e) {
      const detail = e instanceof Error ? e.message : String(e);
      throw new Error(
        `Cannot fetch the latest Chromedriver version. ` +
          `Make sure you can access ${CHROME_FOR_TESTING_LAST_GOOD_VERSIONS} from your machine or provide a mirror by setting ` +
          `a custom value to CHROMELABS_URL environment variable. Original error: ${detail}`,
        {cause: e},
      );
    }
    return parseLatestKnownGoodVersionsJson(jsonStr);
  }

  /**
   * Filters `this.mapping` to only select matching chromedriver entries
   * by operating system information and/or additional synchronization options
   *
   * @param osInfo - Operating system information to match against
   * @param opts - Synchronization options (versions, minBrowserVersion)
   * @returns The list of filtered chromedriver entry names (version/archive name)
   */
  private selectMatchingDrivers(osInfo: OSInfo, opts: SyncOptions = {}): string[] {
    const {minBrowserVersion, versions = []} = opts;
    let driversToSync = Object.keys(this.mapping);

    if (!util.isEmpty(versions)) {
      // Handle only selected versions if requested
      log.debug(`Selecting chromedrivers whose versions match to ${String(versions)}`);
      driversToSync = driversToSync.filter((cdName) => versions.includes(`${this.mapping[cdName].version}`));

      log.debug(`Got ${util.pluralize('item', driversToSync.length, true)}`);
      if (util.isEmpty(driversToSync)) {
        return [];
      }
    }

    const minBrowserVersionInt = convertToInt(minBrowserVersion);
    if (minBrowserVersionInt !== null) {
      // Only select drivers that support the current browser whose major version number equals to `minBrowserVersion`
      log.debug(`Selecting chromedrivers whose minimum supported browser version matches to ${minBrowserVersionInt}`);
      let closestMatchedVersionNumber = 0;
      // Select the newest available and compatible chromedriver
      for (const cdName of driversToSync) {
        const currentMinBrowserVersion = parseInt(String(this.mapping[cdName].minBrowserVersion), 10);
        if (
          !Number.isNaN(currentMinBrowserVersion) &&
          currentMinBrowserVersion <= minBrowserVersionInt &&
          closestMatchedVersionNumber < currentMinBrowserVersion
        ) {
          closestMatchedVersionNumber = currentMinBrowserVersion;
        }
      }
      driversToSync = driversToSync.filter(
        (cdName) =>
          `${this.mapping[cdName].minBrowserVersion}` ===
          `${closestMatchedVersionNumber > 0 ? closestMatchedVersionNumber : minBrowserVersionInt}`,
      );

      log.debug(`Got ${util.pluralize('item', driversToSync.length, true)}`);
      if (util.isEmpty(driversToSync)) {
        return [];
      }
      log.debug(
        `Will select candidate ${util.pluralize('driver', driversToSync.length)} ` +
          `versioned as '${util.uniq(driversToSync.map((cdName) => this.mapping[cdName].version)).join(', ')}'`,
      );
    }

    if (!util.isEmpty(osInfo)) {
      // Filter out drivers for unsupported system architectures
      const {name, arch, cpu = getCpuType()} = osInfo;
      log.debug(`Selecting chromedrivers whose platform matches to ${name}:${cpu}${arch}`);
      let result = driversToSync.filter((cdName) => this.doesMatchForOsInfo(cdName, osInfo));
      if (util.isEmpty(result) && arch === ARCH.X64 && cpu === CPU.INTEL) {
        // Fallback to X86 if X64 architecture is not available for this driver
        result = driversToSync.filter((cdName) =>
          this.doesMatchForOsInfo(cdName, {
            name,
            arch: ARCH.X86,
            cpu,
          }),
        );
      }
      if (util.isEmpty(result) && (name === OS.MAC || name === OS.WINDOWS) && cpu === CPU.ARM) {
        // Fallback to Intel (Rosetta on macOS, x64 emulation on Windows ARM):
        // https://github.com/appium/appium-chromedriver/issues/562
        result = driversToSync.filter((cdName) =>
          this.doesMatchForOsInfo(cdName, {
            name,
            arch,
            cpu: CPU.INTEL,
          }),
        );
      }
      driversToSync = result;
      log.debug(`Got ${util.pluralize('item', driversToSync.length, true)}`);
    }

    if (!util.isEmpty(driversToSync)) {
      log.debug('Excluding older patches if present');
      const patchesMap: {[key: string]: string[]} = {};
      // Older chromedrivers must not be excluded as they follow a different
      // versioning pattern
      const versionWithPatchPattern = /\d+\.\d+\.\d+\.\d+/;
      const selectedVersions = new Set<string>();
      for (const cdName of driversToSync) {
        const cdVersion = this.mapping[cdName].version;
        if (!versionWithPatchPattern.test(cdVersion)) {
          selectedVersions.add(cdVersion);
          continue;
        }
        const verObj = semver.parse(cdVersion, {loose: true});
        if (!verObj) {
          continue;
        }
        if (!Array.isArray(patchesMap[verObj.major])) {
          patchesMap[verObj.major] = [];
        }
        patchesMap[verObj.major].push(cdVersion);
      }
      for (const majorVersion of Object.keys(patchesMap)) {
        if (patchesMap[majorVersion].length <= 1) {
          continue;
        }
        patchesMap[majorVersion].sort((a: string, b: string) => compareVersions(b, a));
      }
      if (!util.isEmpty(patchesMap)) {
        log.debug('Versions mapping: ' + JSON.stringify(patchesMap, null, 2));
        for (const sortedVersions of Object.values(patchesMap)) {
          selectedVersions.add(sortedVersions[0]);
        }
        driversToSync = driversToSync.filter((cdName) => selectedVersions.has(this.mapping[cdName].version));
      }
    }

    return driversToSync;
  }

  /**
   * Checks whether the given chromedriver matches the operating system to run on
   *
   * @param cdName - The chromedriver entry key in the mapping
   * @param osInfo - Operating system information to match against
   * @returns True if the chromedriver matches the OS info
   */
  private doesMatchForOsInfo(cdName: string, {name, arch, cpu}: OSInfo): boolean {
    const cdInfo = this.mapping[cdName];
    if (!cdInfo) {
      return false;
    }

    if (cdInfo.os.name !== name || cdInfo.os.arch !== arch) {
      return false;
    }
    if (cpu && cdInfo.os.cpu && this.mapping[cdName].os.cpu !== cpu) {
      return false;
    }

    return true;
  }

  /**
   * Retrieves the given chromedriver from the storage
   * and unpacks it into `this.chromedriverDir` folder
   *
   * @param driverKey - The driver key in `this.mapping`
   * @param isStrict [true] - Whether to throw an error (`true`)
   * or return a boolean result if the driver retrieval process fails
   * @throws {Error} if there was a failure while retrieving the driver
   * and `isStrict` is set to `true`
   * @returns if `true` then the chromedriver is successfully
   * downloaded and extracted.
   */
  private async retrieveDriver(driverKey: string, isStrict = false): Promise<boolean> {
    const {url, etag, version} = this.mapping[driverKey];
    const fileName = `${path.parse(url).name}_v${version}` + (system.isWindows() ? '.exe' : '');
    const targetPath = path.resolve(this.chromedriverDir, fileName);
    log.debug(`Retrieving '${url}' to '${targetPath}'`);
    try {
      await this.downloadDriver(url, etag, driverKey, targetPath);
      await fs.chmod(targetPath, 0o755);
      log.debug(`Permissions of the file '${targetPath}' have been changed to 755`);
    } catch (e) {
      if (isStrict) {
        throw e;
      }
      log.error((e as Error).message);
      return false;
    }
    return true;
  }

  /**
   * Streams the chromedriver archive from the given URL into a temporary folder
   * without saving the archive itself, verifies its checksum and only then
   * moves the extracted chromedriver executable to the given destination
   *
   * @param url - The archive URL
   * @param etag - The expected MD5 checksum of the archive, if known
   * @param driverKey - The driver key in `this.mapping`
   * @param dst - The destination chromedriver path
   */
  private async downloadDriver(
    url: string,
    etag: string | null | undefined,
    driverKey: string,
    dst: string,
  ): Promise<void> {
    const tmpRoot = await tempDir.openDir();
    try {
      let body: AsyncIterable<Uint8Array>;
      try {
        body = await retrieveData(url, undefined, {timeout: STORAGE_REQ_TIMEOUT_MS, responseType: 'stream'});
      } catch (e) {
        throw downloadError(e);
      }
      const hash = createHash('md5');
      let streamError: unknown;
      try {
        await zip.extractStreamTo(
          hashChunks(body, hash, (err) => {
            streamError = err;
          }),
          tmpRoot,
        );
      } catch (e) {
        // Distinguish network failures from broken archives
        throw streamError ? downloadError(streamError) : e;
      }
      if (etag && hash.digest('hex') !== etag.toLowerCase()) {
        throw new Error(`The checksum for the downloaded chromedriver '${driverKey}' did not match`);
      }

      const chromedriverPath = await fs.walkDir(
        tmpRoot,
        true,
        (itemPath, isDirectory) => !isDirectory && path.parse(itemPath).name.toLowerCase() === 'chromedriver',
      );
      if (!chromedriverPath) {
        throw new Error('The archive was unzipped properly, but we could not find any chromedriver executable');
      }
      log.debug(`Moving the extracted '${path.basename(chromedriverPath)}' to '${dst}'`);
      await fs.mv(chromedriverPath, dst, {
        mkdirp: true,
      });
    } finally {
      await fs.rimraf(tmpRoot);
    }
  }
}

function downloadError(cause: unknown): Error {
  const detail = cause instanceof Error ? cause.message : String(cause);
  return new Error(`Cannot download chromedriver archive. Original error: ${detail}`, {cause});
}

/**
 * Passes the chunks through while feeding them to the hash,
 * and reports the errors of the source stream
 */
async function* hashChunks(
  source: AsyncIterable<Uint8Array>,
  hash: Hash,
  onSourceError: (err: unknown) => void,
): AsyncGenerator<Uint8Array> {
  try {
    for await (const chunk of source) {
      hash.update(chunk);
      yield chunk;
    }
  } catch (e) {
    onSourceError(e);
    throw e;
  }
}
