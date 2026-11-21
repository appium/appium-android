import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import {mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {parseArgs} from 'node:util';

import {zip} from '@appium/support';
import {asyncmap} from 'asyncbox';

const DOWNLOADS_URL = 'https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json';
const LEGACY_STORAGE_URL = 'https://chromedriver.storage.googleapis.com';
const DEFAULT_BACKFILL_COUNT = 0;
const BACKFILL_CONCURRENCY = 4;

const readJson = async (p, fallback) => {
  try {
    return JSON.parse(await readFile(p, 'utf8'));
  } catch (e) {
    if (e.code === 'ENOENT' && fallback !== undefined) {
      return fallback;
    }
    throw e;
  }
};
const writeJson = (p, obj) => writeFile(p, `${JSON.stringify(obj, null, 2)}\n`, 'utf8');

/** Passes the chunks through while feeding them to the hash */
async function* hashChunks(source, hash) {
  for await (const chunk of source) {
    hash.update(chunk);
    yield chunk;
  }
}

/**
 * Streams the archive into a temporary folder without saving the archive itself
 * and returns the SHA-256 of the given archive member.
 * @param {string} url
 * @param {string} member - Path of the executable inside the archive
 * @param {string} tmpRoot
 * @param {string} [expectedMd5] - If set, the archive must have this MD5 checksum
 */
async function sha256OfRemoteZipMember(url, member, tmpRoot, expectedMd5) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Cannot download ${url}: HTTP ${res.status}`);
  }
  const extractDir = await mkdtemp(path.join(tmpRoot, 'extract-'));
  try {
    const md5 = createHash('md5');
    await zip.extractStreamTo(hashChunks(res.body, md5), extractDir);
    if (expectedMd5 && md5.digest('hex') !== expectedMd5) {
      throw new Error(`MD5 mismatch for ${url}`);
    }
    const sha256 = createHash('sha256');
    for await (const chunk of createReadStream(path.join(extractDir, member))) {
      sha256.update(chunk);
    }
    return sha256.digest('hex');
  } finally {
    await rm(extractDir, {recursive: true, force: true});
  }
}

/** Chrome for Testing builds (115+): {platform: sha256 of the executable} */
async function fetchCftChecksums(entry, tmpRoot) {
  const result = {};
  for (const {platform, url} of entry.downloads.chromedriver ?? []) {
    const exe = platform.startsWith('win') ? 'chromedriver.exe' : 'chromedriver';
    result[platform] = await sha256OfRemoteZipMember(url, `chromedriver-${platform}/${exe}`, tmpRoot);
  }
  return result;
}

/** Legacy storage builds (up to 114): {platform: sha256 of the executable}; archives are checked against their MD5 ETag */
async function fetchLegacyChecksums(version, tmpRoot) {
  const res = await fetch(`${LEGACY_STORAGE_URL}/?prefix=${encodeURIComponent(version)}/`);
  if (!res.ok) {
    throw new Error(`Cannot list ${LEGACY_STORAGE_URL} for ${version}: HTTP ${res.status}`);
  }
  const xml = await res.text();
  const result = {};
  for (const [, content] of xml.matchAll(/<Contents>(.*?)<\/Contents>/gs)) {
    const key = /<Key>([^<]+)<\/Key>/.exec(content)?.[1] ?? '';
    const platform = /^[^/]+\/chromedriver_([a-z0-9_]+)\.zip$/.exec(key)?.[1];
    if (!platform) {
      continue;
    }
    const etag = /<ETag>"?([0-9a-f]{32})"?<\/ETag>/.exec(content)?.[1];
    const exe = platform.startsWith('win') ? 'chromedriver.exe' : 'chromedriver';
    result[platform] = await sha256OfRemoteZipMember(`${LEGACY_STORAGE_URL}/${key}`, exe, tmpRoot, etag);
  }
  return result;
}

async function fetchChecksums(version, cftVersions, tmpRoot) {
  const entry = cftVersions.find((v) => v.version === version && v.downloads.chromedriver);
  const result = entry ? await fetchCftChecksums(entry, tmpRoot) : await fetchLegacyChecksums(version, tmpRoot);
  if (Object.keys(result).length === 0) {
    throw new Error(`No chromedriver downloads were found for version ${version}`);
  }
  return result;
}

async function main() {
  const {
    positionals: [latestVersion, mappingPath],
    values,
  } = parseArgs({
    allowPositionals: true,
    options: {
      checksums: {type: 'string'},
      backfill: {type: 'string', default: String(DEFAULT_BACKFILL_COUNT)},
    },
  });
  if (!latestVersion) {
    throw new Error('The latest Chromedriver version must be provided as the first command line argument');
  }
  if (!mappingPath) {
    throw new Error('The path to the versions mapping json must be provided as the second command line argument');
  }
  const checksumsPath = values.checksums ?? path.join(path.dirname(mappingPath), 'checksums.json');
  const backfill = parseInt(values.backfill, 10);

  const mapping = await readJson(mappingPath);
  const checksums = await readJson(checksumsPath, {});
  let changed = false;

  if (!(latestVersion in mapping)) {
    const pairs = Object.entries(mapping);
    pairs.unshift([latestVersion, latestVersion]);
    await writeJson(mappingPath, Object.fromEntries(pairs));
    changed = true;
  }

  // the latest version plus `--backfill N` most recent ones that still have no checksums
  const wanted = [latestVersion, ...Object.keys(mapping)].filter((v, i, arr) => arr.indexOf(v) === i);
  const missing = wanted.filter((v) => !(v in checksums));
  const targets = new Set(missing.filter((v) => v === latestVersion).concat(missing.slice(0, backfill)));
  if (targets.size > 0) {
    const {versions} = await (await fetch(DOWNLOADS_URL)).json();
    const tmpRoot = await mkdtemp(path.join(tmpdir(), 'cd-checksums-'));
    // newest first, same order as the mapping
    const order = Object.keys(mapping).concat(latestVersion);
    const save = () => {
      const sorted = Object.keys(checksums).sort((a, b) => order.indexOf(a) - order.indexOf(b));
      return writeJson(checksumsPath, Object.fromEntries(sorted.map((k) => [k, checksums[k]])));
    };
    const failed = [];
    try {
      await asyncmap(
        [...targets],
        async (version) => {
          try {
            checksums[version] = await fetchChecksums(version, versions, tmpRoot);
            changed = true;
            process.stderr.write(`Computed checksums for ${version}\n`);
          } catch (e) {
            // the latest version must always be covered, older ones are best effort
            if (version === latestVersion) {
              throw e;
            }
            failed.push(version);
            process.stderr.write(`Skipping ${version}: ${e.message}\n`);
          }
        },
        {concurrency: BACKFILL_CONCURRENCY},
      );
    } finally {
      // keep the already computed checksums even if the run is aborted
      await save();
      await rm(tmpRoot, {recursive: true, force: true});
    }
    if (failed.length > 0) {
      process.stderr.write(`Versions without checksums: ${failed.join(', ')}\n`);
    }
  }

  process.stdout.write(changed ? '1' : '0');
}

await main();
