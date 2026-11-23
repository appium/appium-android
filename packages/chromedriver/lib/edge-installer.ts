import {randomUUID} from 'node:crypto';
import {constants, copyFile, mkdir, mkdtemp, rename, rm, chmod} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import {fs, zip} from '@appium/support';

import {trustChecksumOf} from './checksums.js';

const STORAGE_URL = 'https://msedgedriver.microsoft.com';
const VERSION_PATTERN = /^\d+\.\d+\.\d+\.\d+$/;

function platformConfig() {
  switch (process.platform) {
    case 'win32':
      return {
        channel: 'WINDOWS',
        archive: process.arch === 'arm64' ? 'arm64' : process.arch === 'x64' ? 'win64' : 'win32',
      };
    case 'darwin':
      return {channel: 'MACOS', archive: process.arch === 'arm64' ? 'mac64_m1' : 'mac64'};
    case 'linux':
      return {channel: 'LINUX', archive: 'linux64'};
    default:
      throw new Error(`Unsupported platform for MSEdgeDriver autodownload: ${process.platform}`);
  }
}

async function fetchFromMicrosoft(resource: string, timeout: number) {
  const response = await fetch(`${STORAGE_URL}/${resource}`, {signal: AbortSignal.timeout(timeout)});
  if (!response.ok) {
    throw new Error(`Cannot retrieve ${resource} from Microsoft: HTTP ${response.status}`);
  }
  return response;
}

/**
 * Install the official Microsoft Edge WebDriver for this host and return its versioned executable path.
 * Only the freshly downloaded executable is registered in the local checksum store.
 * @param opts.browserVersion - Four-component Microsoft Edge browser version.
 * @param opts.executableDir - Base directory for versioned driver installations.
 */
export async function installEdgeDriver(opts: {browserVersion: string; executableDir: string}): Promise<string> {
  const {browserVersion, executableDir} = opts;
  if (!VERSION_PATTERN.test(browserVersion)) {
    throw new Error(`Invalid Edge browser version: ${browserVersion}`);
  }
  const {channel, archive} = platformConfig();
  const pointer = await fetchFromMicrosoft(`LATEST_RELEASE_${browserVersion.split('.')[0]}_${channel}`, 10_000);
  const bytes = Buffer.from(await pointer.arrayBuffer());
  const version = bytes.toString(bytes[0] === 0xff && bytes[1] === 0xfe ? 'utf16le' : 'utf8').trim();
  if (!VERSION_PATTERN.test(version)) {
    throw new Error(`Invalid Edge WebDriver version returned by Microsoft: ${version}`);
  }

  const name = process.platform === 'win32' ? 'msedgedriver.exe' : 'msedgedriver';
  const targetDir = path.join(executableDir, version);
  const target = path.join(targetDir, name);
  const staging = await mkdtemp(path.join(os.tmpdir(), 'msedgedriver-'));
  try {
    const response = await fetchFromMicrosoft(`${version}/edgedriver_${archive}.zip`, 120_000);
    if (!response.body) {
      throw new Error('Empty Microsoft Edge WebDriver archive response');
    }
    await zip.extractStreamTo(response.body, staging);
    const executable = await fs.walkDir(staging, true, (file, isDir) => !isDir && path.basename(file) === name);
    if (!executable) {
      throw new Error(`Microsoft Edge WebDriver archive is missing ${name}`);
    }
    await chmod(executable, 0o755);
    await trustChecksumOf(executable);
    await mkdir(targetDir, {recursive: true});
    // Publish a complete file even when staging and the cache are on different volumes.
    const pending = path.join(targetDir, `.${name}-${randomUUID()}`);
    try {
      await copyFile(executable, pending, constants.COPYFILE_EXCL);
      await rename(pending, target);
    } finally {
      await rm(pending, {force: true});
    }
    return target;
  } finally {
    await rm(staging, {recursive: true, force: true});
  }
}
