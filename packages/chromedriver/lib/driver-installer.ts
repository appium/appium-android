import path from 'node:path';
import type {Readable} from 'node:stream';

import {system} from '@appium/support';

import {installDriverFromArchive} from './archive-installer.js';
import {ARCH, CPU, OS, STORAGE_REQ_TIMEOUT_MS} from './constants.js';
import {getCpuType, getOsName, retrieveData} from './utils.js';

const MAX_ARCHIVE_BYTES = 200 * 1024 * 1024;
const IDLE_TIMEOUT_MS = 60 * 1000;
const VERSION_PATTERN = /^\d+(\.\d+)*$/;
const PLATFORM_PATTERN = /^[a-z0-9_-]+$/i;

export type DriverBrowser = 'chrome' | 'edge';

export interface InstallDriverFromCdnOptions {
  /** The browser the driver is for. */
  browser: DriverBrowser;
  /**
   * The exact published driver version, e.g. `120.0.6099.109`. It is not the browser version:
   * resolve a compatible driver release first, as the browser patch may have no driver of its own.
   */
  version: string;
  /** Path at which to publish the executable. */
  destination: string;
  /**
   * Base URL of a mirror with the same layout as the default distribution
   * (Chrome for Testing for `chrome`, the Microsoft Edge WebDriver storage for `edge`). Must be http(s).
   */
  cdnUrl?: string;
  /** The vendor platform token (e.g. `win64`, `mac-arm64`). Defaults to the current host. */
  platform?: string;
  /**
   * Whether to register the checksum of the downloaded executable as trusted.
   * If `false`, the executable must already be known (allowlisted or trusted), otherwise the install fails
   * before anything is published at `destination`.
   * Defaults to `true` for the default distributions and to `false` if `cdnUrl` is set.
   */
  trust?: boolean;
}

const DEFAULT_CDN_URLS: Record<DriverBrowser, string> = {
  chrome: 'https://storage.googleapis.com/chrome-for-testing-public',
  edge: 'https://msedgedriver.microsoft.com',
};

/**
 * Downloads a driver of the given exact version from a CDN and installs it with
 * {@linkcode installDriverFromArchive}. A custom `cdnUrl` is not trusted unless the caller says so.
 * @returns The destination path.
 */
export async function installDriverFromCdn(opts: InstallDriverFromCdnOptions): Promise<string> {
  const {browser, version, destination, cdnUrl} = opts;
  const trust = opts.trust ?? !cdnUrl;
  if (!VERSION_PATTERN.test(version)) {
    throw new Error(`Invalid driver version '${version}'`);
  }
  if (opts.platform !== undefined && !PLATFORM_PATTERN.test(opts.platform)) {
    throw new Error(`Invalid driver platform '${opts.platform}'`);
  }
  const url = parseCdnUrl(cdnUrl ?? DEFAULT_CDN_URLS[browser]);
  const platform = opts.platform ?? resolvePlatform(browser, await getHostInfo());
  url.pathname = path.posix.join(
    url.pathname,
    ...(browser === 'chrome'
      ? [version, platform, `chromedriver-${platform}.zip`]
      : [version, `edgedriver_${platform}.zip`]),
  );
  const executableName = getExecutableName(browser, platform);

  let body: AsyncIterable<Uint8Array>;
  try {
    body = await retrieveData(url.toString(), undefined, {timeout: STORAGE_REQ_TIMEOUT_MS, responseType: 'stream'});
  } catch (e) {
    throw new Error(`Cannot download the driver archive from ${url}. Original error: ${(e as Error).message}`, {
      cause: e,
    });
  }
  try {
    return await installDriverFromArchive({
      archive: limitStream(body, {maxBytes: MAX_ARCHIVE_BYTES, idleMs: IDLE_TIMEOUT_MS, url: url.toString()}),
      executableName,
      destination,
      trust,
    });
  } finally {
    // The archive is lazy: if staging fails before it is read, nothing else releases the connection
    (body as Partial<Readable>).destroy?.();
  }
}

function parseCdnUrl(value: string): URL {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`Invalid CDN URL '${value}'`);
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error(`Unsupported CDN URL protocol '${url.protocol}'. Only http(s) is allowed`);
  }
  url.search = '';
  url.hash = '';
  return url;
}

export interface HostInfo {
  os: (typeof OS)[keyof typeof OS];
  arch: 'x86' | 'x64' | 'arm64' | 'other';
}

export interface HostFacts {
  os: HostInfo['os'];
  /** `process.arch` */
  processArch: string;
  /** The OS bitness as reported by `system.arch()`: `'32'` or `'64'` */
  bitness: string;
  cpu: (typeof CPU)[keyof typeof CPU];
}

/** Classifies the host the way the drivers' distributions distinguish platforms. */
export function classifyHost({os: osName, processArch, bitness, cpu}: HostFacts): HostInfo {
  switch (osName) {
    case OS.WINDOWS:
      // `system.arch()` can report '32' for a native ARM64 process, so check it first
      if (processArch === 'arm64') {
        return {os: osName, arch: 'arm64'};
      }
      return {os: osName, arch: bitness === ARCH.X86 ? 'x86' : 'x64'};
    case OS.MAC:
      return {os: osName, arch: cpu === CPU.ARM ? 'arm64' : 'x64'};
    default:
      return {os: osName, arch: processArch === 'arm64' ? 'arm64' : processArch === 'x64' ? 'x64' : 'other'};
  }
}

/** Describes the current host. */
export async function getHostInfo(): Promise<HostInfo> {
  return classifyHost({
    os: getOsName(),
    processArch: process.arch,
    bitness: String(await system.arch()),
    cpu: getCpuType(),
  });
}

/** Returns the vendor platform token of the browser's driver distribution for the host. */
export function resolvePlatform(browser: DriverBrowser, host: HostInfo): string {
  const {os: osName, arch} = host;
  const unsupported = () =>
    new Error(`There are no prebuilt ${browser} drivers for ${osName}/${arch}. Provide the 'platform' explicitly`);
  switch (osName) {
    case OS.WINDOWS:
      if (arch === 'x86') {
        return 'win32';
      }
      // Chrome for Testing has no native Windows ARM64 driver, the x64 one runs emulated
      return browser === 'edge' && arch === 'arm64' ? 'arm64' : 'win64';
    case OS.MAC:
      if (arch !== 'x64' && arch !== 'arm64') {
        throw unsupported();
      }
      if (browser === 'chrome') {
        return arch === 'arm64' ? 'mac-arm64' : 'mac-x64';
      }
      return arch === 'arm64' ? 'mac64_m1' : 'mac64';
    default:
      if (arch === 'x64') {
        return 'linux64';
      }
      if (arch === 'arm64' && browser === 'chrome') {
        return 'linux-arm64';
      }
      throw unsupported();
  }
}

/** Returns the basename of the driver executable inside the archive of the given platform. */
export function getExecutableName(browser: DriverBrowser, platform: string): string {
  const name = browser === 'chrome' ? 'chromedriver' : 'msedgedriver';
  const isWindows = platform.startsWith('win') || (browser === 'edge' && platform === 'arm64');
  return isWindows ? `${name}.exe` : name;
}

/** Passes the chunks through, failing if the stream exceeds `maxBytes` or stalls for `idleMs`. */
export async function* limitStream(
  source: AsyncIterable<Uint8Array>,
  opts: {maxBytes: number; idleMs: number; url: string},
): AsyncGenerator<Uint8Array> {
  const {maxBytes, idleMs, url} = opts;
  const iterator = source[Symbol.asyncIterator]();
  let received = 0;
  let stalled = false;
  try {
    for (;;) {
      let timer: NodeJS.Timeout | undefined;
      const timeout = new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => {
          stalled = true;
          // Closes the connection, so the pending read ends and cleanup is not blocked behind it
          (source as Partial<Readable>).destroy?.();
          reject(new Error(`The download from ${url} stalled for ${idleMs} ms`));
        }, idleMs);
      });
      let next: IteratorResult<Uint8Array>;
      try {
        next = await Promise.race([iterator.next(), timeout]);
      } finally {
        clearTimeout(timer);
      }
      if (next.done) {
        return;
      }
      received += next.value.byteLength;
      if (received > maxBytes) {
        throw new Error(`The download from ${url} exceeds ${maxBytes} bytes`);
      }
      yield next.value;
    }
  } finally {
    if (!stalled) {
      // Closes the underlying connection if we stop early
      await iterator.return?.();
    }
  }
}
