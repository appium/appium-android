import {asyncmap} from 'asyncbox';

const EDGE_STORAGE_URL = 'https://msedgedriver.microsoft.com';
const POINTER_CONCURRENCY = 8;
const VERSION_PATTERN = /^\d+(\.\d+){3}$/;
const OS_SUFFIXES = ['', '_WINDOWS', '_LINUX', '_MACOS'];
// `arm64` is the Windows ARM64 build
const exeName = (platform) =>
  platform.startsWith('win') || platform === 'arm64' ? 'msedgedriver.exe' : 'msedgedriver';

/** Microsoft Edge WebDriver builds */
export class EdgeSource {
  /** Reads a `LATEST_*` pointer. Returns `null` if it does not exist (404), any other failure is thrown */
  async getLatest(name) {
    const res = await fetch(`${EDGE_STORAGE_URL}/${name}`);
    if (res.status === 404) {
      return null;
    }
    if (!res.ok) {
      throw new Error(`Cannot read ${name}: HTTP ${res.status}`);
    }
    const buf = Buffer.from(await res.arrayBuffer());
    // the pointers are UTF-16LE with BOM
    const text = (buf[0] === 0xff && buf[1] === 0xfe ? buf.toString('utf16le') : buf.toString('utf8'))
      .replace(/^﻿/, '')
      .trim();
    if (!VERSION_PATTERN.test(text)) {
      throw new Error(`${name} has an unexpected content: '${text.slice(0, 100)}'`);
    }
    return text;
  }

  /**
   * Stable releases: the latest stable and extended stable ones
   * and the latest one of each of the given majors (per OS)
   * @param {number[]} majors
   * @returns {Promise<string[]>}
   */
  async getReleaseVersions(majors) {
    const names = [
      'LATEST_STABLE',
      'LATEST_EXTENDEDSTABLE',
      ...majors.flatMap((m) => OS_SUFFIXES.map((suffix) => `LATEST_RELEASE_${m}${suffix}`)),
    ];
    const versions = await asyncmap(names, (name) => this.getLatest(name), {concurrency: POINTER_CONCURRENCY});
    return [...new Set(versions.filter(Boolean))];
  }

  /** Lists the archives of the version: {platform: md5 (hex)} */
  async #listArchives(version) {
    const res = await fetch(`${EDGE_STORAGE_URL}/?restype=container&comp=list&prefix=${encodeURIComponent(version)}/`);
    if (!res.ok) {
      throw new Error(`Cannot list the archives of ${version}: HTTP ${res.status}`);
    }
    const xml = await res.text();
    const result = {};
    for (const [, content] of xml.matchAll(/<Blob>(.*?)<\/Blob>/gs)) {
      const platform = /<Name>[^/]+\/edgedriver_([a-z0-9_]+)\.zip<\/Name>/.exec(content)?.[1];
      if (!platform) {
        continue;
      }
      const md5Base64 = /<Content-MD5>([^<]+)<\/Content-MD5>/.exec(content)?.[1];
      if (!md5Base64) {
        // never generate a checksum for an archive that cannot be verified
        throw new Error(`The listing has no Content-MD5 for ${version}/edgedriver_${platform}.zip`);
      }
      result[platform] = Buffer.from(md5Base64, 'base64').toString('hex');
    }
    return result;
  }

  async fetchChecksums(version, hasher) {
    const result = {};
    for (const [platform, expectedMd5] of Object.entries(await this.#listArchives(version))) {
      result[platform] = await hasher.sha256OfMember(
        `${EDGE_STORAGE_URL}/${version}/edgedriver_${platform}.zip`,
        exeName(platform),
        {expectedMd5},
      );
    }
    if (Object.keys(result).length === 0) {
      throw new Error(`No Edge WebDriver archives were found for version ${version}`);
    }
    return result;
  }
}
