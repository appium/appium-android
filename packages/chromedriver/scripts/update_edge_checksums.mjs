import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {parseArgs} from 'node:util';

import {asyncmap} from 'asyncbox';

import {readJson, sha256OfRemoteZipMember, writeJson} from './checksums-utils.mjs';

const EDGE_STORAGE_URL = 'https://msedgedriver.microsoft.com';
// the oldest major that is still available in the storage
const MIN_MAJOR = 118;
const CONCURRENCY = 4;
const VERSION_PATTERN = /^\d+(\.\d+){3}$/;
const OS_SUFFIXES = ['', '_WINDOWS', '_LINUX', '_MACOS'];

const major = (version) => parseInt(version, 10);
const compareVersions = (a, b) => {
  const [pa, pb] = [a, b].map((v) => v.split('.').map(Number));
  return pa.reduce((acc, n, i) => acc || n - pb[i], 0);
};

/** Reads a `LATEST_*` pointer. Returns `null` if it does not exist */
async function getLatest(name) {
  const res = await fetch(`${EDGE_STORAGE_URL}/${name}`);
  if (!res.ok) {
    return null;
  }
  const buf = Buffer.from(await res.arrayBuffer());
  // the pointers are UTF-16LE with BOM, errors are XML
  const text = (buf[0] === 0xff && buf[1] === 0xfe ? buf.toString('utf16le') : buf.toString('utf8'))
    .replace(/^﻿/, '')
    .trim();
  return VERSION_PATTERN.test(text) ? text : null;
}

/** Stable releases: the latest stable/extended stable ones and the latest of each major (per OS) */
async function getReleaseVersions(majors) {
  const names = [
    'LATEST_STABLE',
    'LATEST_EXTENDEDSTABLE',
    ...majors.flatMap((m) => OS_SUFFIXES.map((s) => `LATEST_RELEASE_${m}${s}`)),
  ];
  const versions = await asyncmap(names, getLatest, {concurrency: CONCURRENCY});
  return new Set(versions.filter(Boolean));
}

/** Lists the archives of the version: {platform: expected md5 (hex)} */
async function listArchives(version) {
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
    result[platform] = md5Base64 ? Buffer.from(md5Base64, 'base64').toString('hex') : undefined;
  }
  return result;
}

/** {platform: sha256 of the executable} */
async function fetchChecksums(version, tmpRoot) {
  const archives = await listArchives(version);
  const result = {};
  for (const [platform, md5] of Object.entries(archives)) {
    // `arm64` is the Windows ARM64 build
    const exe = platform.startsWith('win') || platform === 'arm64' ? 'msedgedriver.exe' : 'msedgedriver';
    result[platform] = await sha256OfRemoteZipMember(
      `${EDGE_STORAGE_URL}/${version}/edgedriver_${platform}.zip`,
      exe,
      tmpRoot,
      md5,
    );
  }
  if (Object.keys(result).length === 0) {
    throw new Error(`No Edge WebDriver archives were found for version ${version}`);
  }
  return result;
}

async function main() {
  const {
    positionals: [checksumsPath],
    values: {backfill},
  } = parseArgs({
    allowPositionals: true,
    options: {backfill: {type: 'boolean', default: false}},
  });
  if (!checksumsPath) {
    throw new Error('The path to the Edge WebDriver checksums json must be provided as the first argument');
  }

  const checksums = await readJson(checksumsPath, {});
  const stable = await getLatest('LATEST_STABLE');
  if (!stable) {
    throw new Error('Cannot determine the latest stable Edge WebDriver version');
  }
  // by default only the majors that are currently supported, `--backfill` goes through all available ones
  const extended = await getLatest('LATEST_EXTENDEDSTABLE');
  const majors = backfill
    ? Array.from({length: major(stable) - MIN_MAJOR + 1}, (_, i) => MIN_MAJOR + i)
    : [...new Set([major(stable), extended && major(extended)].filter(Boolean))];
  const targets = [...(await getReleaseVersions(majors))].filter((v) => !(v in checksums));
  if (targets.length === 0) {
    process.stdout.write('0');
    return;
  }

  const tmpRoot = await mkdtemp(path.join(tmpdir(), 'edge-checksums-'));
  const failed = [];
  try {
    await asyncmap(
      targets,
      async (version) => {
        try {
          checksums[version] = await fetchChecksums(version, tmpRoot);
          process.stderr.write(`Computed checksums for ${version}\n`);
        } catch (e) {
          // the latest stable version must always be covered, others are best effort
          if (version === stable) {
            throw e;
          }
          failed.push(version);
          process.stderr.write(`Skipping ${version}: ${e.message}\n`);
        }
      },
      {concurrency: CONCURRENCY},
    );
  } finally {
    // keep the already computed checksums even if the run is aborted
    const sorted = Object.keys(checksums).sort((a, b) => compareVersions(b, a));
    await writeJson(checksumsPath, Object.fromEntries(sorted.map((v) => [v, checksums[v]])));
    await rm(tmpRoot, {recursive: true, force: true});
  }
  if (failed.length > 0) {
    process.stderr.write(`Versions without checksums: ${failed.join(', ')}\n`);
  }
  process.stdout.write('1');
}

await main();
