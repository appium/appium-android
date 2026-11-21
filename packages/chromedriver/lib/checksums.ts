import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';

import {util} from '@appium/support';
import {AsyncLock} from 'asyncbox';

import {getModuleRoot} from './utils.js';

/** Driver version -> platform -> SHA-256 of the executable */
export type ChecksumsMapping = Record<string, Record<string, string>>;

// Local trust store for binaries downloaded by this module (dotfile, so it is never listed as a driver)
export const TRUSTED_CHECKSUMS_FILE = '.trusted-checksums.json';

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

const TRUST_STORE_GUARD = new AsyncLock();

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
  // drivers are downloaded in parallel, so serialize read-modify-write cycles of the trust file
  await TRUST_STORE_GUARD.acquire(path.resolve(trustDir), async () => {
    const trusted = await readTrustedChecksums(trustDir);
    if (trusted.has(sha256)) {
      return;
    }
    trusted.add(sha256);
    const dst = path.join(trustDir, TRUSTED_CHECKSUMS_FILE);
    const tmp = `${dst}.${process.pid}.tmp`;
    await fs.writeFile(tmp, JSON.stringify([...trusted].sort(), null, 2), 'utf8');
    await fs.rename(tmp, dst);
  });
}

/**
 * Makes sure the file is a known Chromedriver or Microsoft Edge WebDriver build without executing it.
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
    `The SHA-256 checksum (${sha256}) of '${binPath}' does not match any known Chromedriver or Edge WebDriver build. ` +
      `Use a driver build distributed by Google or Microsoft, or enable the ` +
      `'chromedriver_allow_any_executable' server feature to allow arbitrary executables`,
  );
}
