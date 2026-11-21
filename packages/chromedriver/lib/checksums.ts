import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';

import {util} from '@appium/support';

import {getModuleRoot} from './utils.js';

/** Chromedriver version -> platform -> SHA-256 of the executable */
export type ChecksumsMapping = Record<string, Record<string, string>>;

// Local trust store for binaries downloaded by this module (dotfile, so it is never listed as a driver)
export const TRUSTED_CHECKSUMS_FILE = '.trusted-checksums.json';

/** Reads the shipped allowlist on the first call only */
export const getChromedriverChecksums = util.memoize(
  async function getChromedriverChecksums(): Promise<ChecksumsMapping> {
    const content = await fs.readFile(path.join(getModuleRoot(), 'config', 'checksums.json'), 'utf8');
    return JSON.parse(content) as ChecksumsMapping;
  },
);

const getKnownChecksums = util.memoize(async function getKnownChecksums(): Promise<Set<string>> {
  const checksums = await getChromedriverChecksums();
  return new Set(Object.values(checksums).flatMap((perPlatform) => Object.values(perPlatform)));
});

let trustWriteQueue: Promise<void> = Promise.resolve();

// Always hashes the current content (~10-30 ms): size/mtime based caching could be defeated by a swapped file
export async function sha256File(filePath: string): Promise<string> {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(filePath)) {
    hash.update(chunk);
  }
  return hash.digest('hex');
}

async function readTrustedChecksums(trustDir: string): Promise<Set<string>> {
  try {
    const parsed: unknown = JSON.parse(await fs.readFile(path.join(trustDir, TRUSTED_CHECKSUMS_FILE), 'utf8'));
    return new Set(Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : []);
  } catch {
    return new Set();
  }
}

/**
 * Remembers the checksum of a binary this module has downloaded and verified itself,
 * so it passes {@linkcode assertKnownChromedriver} even if it is newer than the shipped allowlist.
 */
export async function trustChecksumOf(binPath: string, trustDir: string): Promise<void> {
  const sha256 = await sha256File(binPath);
  // serialize read-modify-write cycles of the trust file (drivers are downloaded in parallel)
  const previous = trustWriteQueue;
  let release!: () => void;
  trustWriteQueue = new Promise<void>((resolve) => (release = resolve));
  await previous;
  try {
    const trusted = await readTrustedChecksums(trustDir);
    if (trusted.has(sha256)) {
      return;
    }
    trusted.add(sha256);
    const dst = path.join(trustDir, TRUSTED_CHECKSUMS_FILE);
    const tmp = `${dst}.${process.pid}.tmp`;
    await fs.writeFile(tmp, JSON.stringify([...trusted].sort(), null, 2), 'utf8');
    await fs.rename(tmp, dst);
  } finally {
    release();
  }
}

/**
 * Makes sure the file is a known Chromedriver build without executing it.
 * @param binPath - Path to the executable
 * @param trustDir - Folder with locally trusted checksums (the chromedriver download folder)
 * @throws {Error} If the SHA-256 of the file is not on the allowlist
 */
export async function assertKnownChromedriver(binPath: string, trustDir?: string): Promise<void> {
  const sha256 = await sha256File(binPath);
  if ((await getKnownChecksums()).has(sha256) || (trustDir && (await readTrustedChecksums(trustDir)).has(sha256))) {
    return;
  }
  throw new Error(
    `The SHA-256 checksum (${sha256}) of '${binPath}' does not match any known Chromedriver build. ` +
      `Use a Chromedriver build distributed by Google, or explicitly allow arbitrary executables ` +
      `(the 'chromedriver_allow_any_executable' server feature when used via Appium)`,
  );
}
