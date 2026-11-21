import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';

import {zip} from '@appium/support';

/** Passes the chunks through while feeding them to the hash */
async function* hashChunks(source, hash) {
  for await (const chunk of source) {
    hash.update(chunk);
    yield chunk;
  }
}

/**
 * Streams remote zip archives into a temporary folder (the archives themselves are never saved)
 * and hashes the executables inside. Use {@linkcode ArchiveHasher.withTempDir} to manage the folder.
 */
export class ArchiveHasher {
  #tmpRoot;

  constructor(tmpRoot) {
    this.#tmpRoot = tmpRoot;
  }

  /**
   * @template T
   * @param {(hasher: ArchiveHasher) => Promise<T>} fn
   * @returns {Promise<T>}
   */
  static async withTempDir(fn) {
    const tmpRoot = await mkdtemp(path.join(tmpdir(), 'driver-checksums-'));
    try {
      return await fn(new ArchiveHasher(tmpRoot));
    } finally {
      await rm(tmpRoot, {recursive: true, force: true});
    }
  }

  /**
   * @param {string} url - The archive URL
   * @param {string} member - Path of the executable inside the archive
   * @param {{expectedMd5?: string}} [opts] - If `expectedMd5` is set, the archive must have this MD5 (hex)
   * @returns {Promise<string>} SHA-256 of the executable
   */
  async sha256OfMember(url, member, {expectedMd5} = {}) {
    const res = await fetch(url);
    if (!res.ok) {
      throw new Error(`Cannot download ${url}: HTTP ${res.status}`);
    }
    const extractDir = await mkdtemp(path.join(this.#tmpRoot, 'extract-'));
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
}
