import path from 'node:path';

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
  /** The exact driver version, e.g. `120.0.6099.109`. */
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
   * If `false`, the executable must be on the shipped allowlist to be started.
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
  const platform = opts.platform ?? (await getHostPlatform(browser));
  url.pathname = path.posix.join(
    url.pathname,
    ...(browser === 'chrome'
      ? [version, platform, `chromedriver-${platform}.zip`]
      : [version, `edgedriver_${platform}.zip`]),
  );
  const name = browser === 'chrome' ? 'chromedriver' : 'msedgedriver';
  const isWindows = platform.startsWith('win') || (browser === 'edge' && platform === 'arm64');
  const executableName = isWindows ? `${name}.exe` : name;

  let body: AsyncIterable<Uint8Array>;
  try {
    body = await retrieveData(url.toString(), undefined, {timeout: STORAGE_REQ_TIMEOUT_MS, responseType: 'stream'});
  } catch (e) {
    throw new Error(`Cannot download the driver archive from ${url}. Original error: ${(e as Error).message}`, {
      cause: e,
    });
  }
  return await installDriverFromArchive({
    archive: limitStream(body, {maxBytes: MAX_ARCHIVE_BYTES, idleMs: IDLE_TIMEOUT_MS, url: url.toString()}),
    executableName,
    destination,
    trust,
  });
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

async function getHostPlatform(browser: DriverBrowser): Promise<string> {
  const osName = getOsName();
  const isArm = getCpuType() === CPU.ARM;
  switch (osName) {
    case OS.WINDOWS:
      if (browser === 'edge' && process.arch === 'arm64') {
        return 'arm64';
      }
      return String(await system.arch()) === ARCH.X86 ? 'win32' : 'win64';
    case OS.MAC:
      return browser === 'chrome' ? (isArm ? 'mac-arm64' : 'mac-x64') : isArm ? 'mac64_m1' : 'mac64';
    default:
      if (process.arch !== 'x64') {
        throw new Error(`There are no prebuilt drivers for linux/${process.arch}. Provide the 'platform' explicitly`);
      }
      return 'linux64';
  }
}

/** Passes the chunks through, failing if the stream exceeds `maxBytes` or stalls for `idleMs`. */
export async function* limitStream(
  source: AsyncIterable<Uint8Array>,
  opts: {maxBytes: number; idleMs: number; url: string},
): AsyncGenerator<Uint8Array> {
  const {maxBytes, idleMs, url} = opts;
  const iterator = source[Symbol.asyncIterator]();
  let received = 0;
  try {
    for (;;) {
      let timer: NodeJS.Timeout | undefined;
      const stalled = new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => reject(new Error(`The download from ${url} stalled for ${idleMs} ms`)), idleMs);
      });
      let next: IteratorResult<Uint8Array>;
      try {
        next = await Promise.race([iterator.next(), stalled]);
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
    // Closes the underlying connection if we stop early
    await iterator.return?.();
  }
}
