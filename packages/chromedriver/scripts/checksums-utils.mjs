import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import {mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import path from 'node:path';

import {zip} from '@appium/support';

export const readJson = async (p, fallback) => {
  try {
    return JSON.parse(await readFile(p, 'utf8'));
  } catch (e) {
    if (e.code === 'ENOENT' && fallback !== undefined) {
      return fallback;
    }
    throw e;
  }
};
export const writeJson = (p, obj) => writeFile(p, `${JSON.stringify(obj, null, 2)}\n`, 'utf8');

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
export async function sha256OfRemoteZipMember(url, member, tmpRoot, expectedMd5) {
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
