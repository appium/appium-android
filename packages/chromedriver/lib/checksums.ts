import {createHash} from 'node:crypto';
import {createReadStream, readFileSync} from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';

import {getModuleRoot} from './utils.js';

/** Chromedriver version -> platform -> SHA-256 of the executable */
export type ChecksumsMapping = Record<string, Record<string, string>>;

// Local trust store for binaries downloaded by this module (dotfile, so it is never listed as a driver)
export const TRUSTED_CHECKSUMS_FILE = '.trusted-checksums.json';

export const CHROMEDRIVER_CHECKSUMS: ChecksumsMapping = JSON.parse(
  readFileSync(path.join(getModuleRoot(), 'config', 'checksums.json'), 'utf8'),
);

const knownChecksums = new Set(
  Object.values(CHROMEDRIVER_CHECKSUMS).flatMap((perPlatform) => Object.values(perPlatform)),
);
const hashCache = new Map<string, {key: string; sha256: string}>();
let trustWriteQueue: Promise<void> = Promise.resolve();

export async function sha256File(filePath: string): Promise<string> {
  const {size, mtimeMs} = await fs.stat(filePath);
  const key = `${size}:${mtimeMs}`;
  const cached = hashCache.get(filePath);
  if (cached?.key === key) {
    return cached.sha256;
  }
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(filePath)) {
    hash.update(chunk);
  }
  const sha256 = hash.digest('hex');
  hashCache.set(filePath, {key, sha256});
  return sha256;
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
  if (knownChecksums.has(sha256) || (trustDir && (await readTrustedChecksums(trustDir)).has(sha256))) {
    return;
  }
  throw new Error(
    `The SHA-256 checksum (${sha256}) of '${binPath}' does not match any known Chromedriver build. ` +
      `Use a Chromedriver build distributed by Google, or explicitly allow arbitrary executables ` +
      `(the 'chromedriver_allow_any_executable' server feature when used via Appium)`,
  );
}
