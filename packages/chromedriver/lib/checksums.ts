import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';

import {util} from '@appium/support';

import {CD_BASE_DIR, getModuleRoot} from './utils.js';

/** Driver version -> platform -> SHA-256 of the executable */
export type ChecksumsMapping = Record<string, Record<string, string>>;

export const TRUST_STORE_DIR_ENV = 'APPIUM_CHROMEDRIVER_TRUST_STORE_DIR';

/**
 * Server-side folder with the checksums of the drivers downloaded by this module.
 * It is deliberately independent of the (session-provided) executable folder.
 */
export const getTrustStoreDir = (): string =>
  process.env[TRUST_STORE_DIR_ENV] || path.join(CD_BASE_DIR, '.trusted-checksums');

const readChecksumsFile = util.memoize(async function readChecksumsFile(fileName: string): Promise<ChecksumsMapping> {
  const content = await fs.readFile(path.join(getModuleRoot(), 'config', fileName), 'utf8');
  return JSON.parse(content) as ChecksumsMapping;
});

/** Reads the shipped Chromedriver allowlist on the first call only */
export const getChromedriverChecksums = (): Promise<ChecksumsMapping> => readChecksumsFile('checksums.json');

/** Reads the shipped Microsoft Edge WebDriver allowlist on the first call only */
export const getEdgeDriverChecksums = (): Promise<ChecksumsMapping> => readChecksumsFile('edge-checksums.json');

const getKnownChecksums = util.memoize(async function getKnownChecksums(): Promise<Set<string>> {
  const mappings = await Promise.all([getChromedriverChecksums(), getEdgeDriverChecksums()]);
  return new Set(
    mappings.flatMap((mapping) => Object.values(mapping).flatMap((perPlatform) => Object.values(perPlatform))),
  );
});

// Always hashes the current content (~10-30 ms): size/mtime based caching could be defeated by a swapped file
export async function sha256File(filePath: string): Promise<string> {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(filePath)) {
    hash.update(chunk);
  }
  return hash.digest('hex');
}

// One empty file per checksum: adding is idempotent and needs no locking, even across processes
async function isTrusted(sha256: string, trustDir: string): Promise<boolean> {
  try {
    await fs.access(path.join(trustDir, sha256));
    return true;
  } catch {
    return false;
  }
}

/**
 * Remembers the checksum of a binary this module has downloaded and verified itself,
 * so it passes {@linkcode assertKnownChromedriver} even if it is newer than the shipped allowlist.
 */
export async function trustChecksumOf(binPath: string, trustDir: string = getTrustStoreDir()): Promise<void> {
  const sha256 = await sha256File(binPath);
  await fs.mkdir(trustDir, {recursive: true});
  await fs.writeFile(path.join(trustDir, sha256), '', {flag: 'a'});
}

/**
 * Makes sure the file is a known Chromedriver or Microsoft Edge WebDriver build without executing it.
 * @param binPath - Path to the executable
 * @param trustDir - Folder with locally trusted checksums (the server-side trust store by default)
 * @throws {Error} If the SHA-256 of the file is not on the allowlist
 */
export async function assertKnownChromedriver(binPath: string, trustDir: string = getTrustStoreDir()): Promise<void> {
  const sha256 = await sha256File(binPath);
  if ((await getKnownChecksums()).has(sha256) || (await isTrusted(sha256, trustDir))) {
    return;
  }
  throw new Error(
    `The SHA-256 checksum (${sha256}) of '${binPath}' does not match any known Chromedriver or Edge WebDriver build. ` +
      `Use a driver build distributed by Google or Microsoft, or enable the ` +
      `'chromedriver_allow_any_executable' server feature to allow arbitrary executables`,
  );
}
