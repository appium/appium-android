import {randomUUID} from 'node:crypto';
import {copyFile, mkdtemp, rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import {fs, zip} from '@appium/support';

import {trustChecksumOf} from './checksums.js';

/**
 * Install a driver from a trusted ZIP stream and register its SHA-256 for future launches.
 * The caller selects the distribution source and is responsible for its authenticity.
 * @param opts.archive - ZIP bytes from the trusted download.
 * @param opts.executableName - Basename of the single regular file to install from the ZIP.
 * @param opts.destination - Path at which to publish the executable.
 * @returns The destination path.
 */
export async function installDriverFromArchive(opts: {
  archive: AsyncIterable<Uint8Array>;
  executableName: string;
  destination: string;
}): Promise<string> {
  const {archive, executableName, destination} = opts;
  const temporaryRoot = process.env.APPIUM_TMP_DIR || os.tmpdir();
  await fs.mkdirp(temporaryRoot);
  // tempDir.openDir() uses recursive mkdir and can reuse an existing directory.
  // Trust registration needs fresh, private staging under Appium's configured temporary root.
  const staging = await mkdtemp(path.resolve(temporaryRoot, 'driver-archive-'));
  try {
    await zip.extractStreamTo(archive, staging);
    const entries = await fs.readdir(staging, {recursive: true, withFileTypes: true});
    const matches = entries.filter((entry) => entry.isFile() && entry.name === executableName);
    if (matches.length !== 1) {
      throw new Error(`Expected one executable named '${executableName}' in the archive, found ${matches.length}`);
    }
    const executable = path.join(matches[0].parentPath, matches[0].name);
    await fs.chmod(executable, 0o755);
    const destinationDir = path.dirname(destination);
    await fs.mkdirp(destinationDir);
    // Publish a complete file even when staging and the destination are on different volumes.
    const pending = path.join(destinationDir, `.driver-${randomUUID()}`);
    await copyFile(executable, pending, fs.constants.COPYFILE_EXCL);
    try {
      await trustChecksumOf(executable);
      await fs.rename(pending, destination);
    } finally {
      await rm(pending, {force: true});
    }
    return destination;
  } finally {
    await fs.rimraf(staging);
  }
}
