import assert from 'node:assert/strict';
import http from 'node:http';
import type {AddressInfo} from 'node:net';
import path from 'node:path';
import {after, before, describe, it} from 'node:test';

import {fs, tempDir, zip} from '@appium/support';

import {TRUST_STORE_SUFFIX_ENV, assertKnownChromedriver, getTrustStoreDir} from '../../lib/checksums.js';
import {
  classifyHost,
  getExecutableName,
  getHostInfo,
  limitStream,
  resolvePlatform,
} from '../../lib/driver-installer.js';
import type {DriverBrowser, HostFacts, HostInfo} from '../../lib/driver-installer.js';
import {installDriverFromCdn} from '../../lib/index.js';
import {retrieveData} from '../../lib/utils.js';

describe('installDriverFromCdn', function () {
  let root: string;
  let trustStoreDir: string;
  let previousSuffix: string | undefined;
  let server: http.Server;
  let baseUrl: string;
  const requested: string[] = [];
  const archives = new Map<string, Buffer>();

  async function makeArchive(name: string, content: string): Promise<Buffer> {
    const src = path.join(root, `src-${name}-${content}`);
    await fs.mkdirp(path.join(src, 'nested'));
    await fs.writeFile(path.join(src, 'nested', name), content);
    return await zip.toInMemoryZip(src);
  }

  before(async function () {
    root = await tempDir.openDir();
    previousSuffix = process.env[TRUST_STORE_SUFFIX_ENV];
    process.env[TRUST_STORE_SUFFIX_ENV] = `cdn-install-test-${path.basename(root)}`;
    // Captured while the test suffix is active, so teardown never touches the real trust store
    trustStoreDir = getTrustStoreDir();
    archives.set('/120.0.1.2/win64/chromedriver-win64.zip', await makeArchive('chromedriver.exe', 'chrome-win'));
    archives.set('/120.0.1.2/edgedriver_win64.zip', await makeArchive('msedgedriver.exe', 'edge-win'));
    archives.set('/120.0.1.2/linux64/chromedriver-linux64.zip', await makeArchive('chromedriver', 'chrome-linux'));
    archives.set('/prefix/120.0.1.2/edgedriver_mac64.zip', await makeArchive('msedgedriver', 'edge-mac'));
    server = http.createServer((req, res) => {
      requested.push(req.url ?? '');
      const body = archives.get(req.url ?? '');
      if (!body) {
        res.writeHead(404).end();
        return;
      }
      res.writeHead(200, {'content-type': 'application/zip'}).end(body);
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });

  after(async function () {
    if (previousSuffix === undefined) {
      delete process.env[TRUST_STORE_SUFFIX_ENV];
    } else {
      process.env[TRUST_STORE_SUFFIX_ENV] = previousSuffix;
    }
    await new Promise((resolve) => server.close(resolve));
    await fs.rimraf(trustStoreDir);
    await fs.rimraf(root);
  });

  it('installs and trusts a Chrome driver using the Chrome for Testing layout', async function () {
    const destination = path.join(root, 'chrome', 'chromedriver.exe');
    const result = await installDriverFromCdn({
      browser: 'chrome',
      version: '120.0.1.2',
      platform: 'win64',
      cdnUrl: baseUrl,
      trust: true,
      destination,
    });
    assert.equal(result, destination);
    assert.equal(await fs.readFile(destination, 'utf8'), 'chrome-win');
    await assertKnownChromedriver(destination);
    assert.ok(requested.includes('/120.0.1.2/win64/chromedriver-win64.zip'));
  });

  it('installs and trusts an Edge driver using the Edge layout, keeping the CDN path prefix', async function () {
    const destination = path.join(root, 'edge', 'msedgedriver');
    await installDriverFromCdn({
      browser: 'edge',
      version: '120.0.1.2',
      platform: 'mac64',
      cdnUrl: `${baseUrl}/prefix`,
      trust: true,
      destination,
    });
    assert.equal(await fs.readFile(destination, 'utf8'), 'edge-mac');
    await assertKnownChromedriver(destination);
  });

  it('rejects an unknown driver from a custom CDN by default and publishes nothing', async function () {
    const destination = path.join(root, 'untrusted', 'chromedriver');
    await assert.rejects(
      installDriverFromCdn({
        browser: 'chrome',
        version: '120.0.1.2',
        platform: 'linux64',
        cdnUrl: baseUrl,
        destination,
      }),
      /does not match any known/,
    );
    assert.equal(await fs.exists(destination), false);
  });

  it('preserves an existing driver when the download is rejected', async function () {
    const destination = path.join(root, 'cache', 'chromedriver');
    await fs.mkdirp(path.dirname(destination));
    await fs.writeFile(destination, 'working-driver');
    await assert.rejects(
      installDriverFromCdn({
        browser: 'chrome',
        version: '120.0.1.2',
        platform: 'linux64',
        cdnUrl: baseUrl,
        trust: false,
        destination,
      }),
      /does not match any known/,
    );
    assert.equal(await fs.readFile(destination, 'utf8'), 'working-driver');
    assert.deepEqual(await fs.readdir(path.dirname(destination)), ['chromedriver']);
  });

  it('installs an already trusted driver from a custom CDN with trust: false', async function () {
    // Make the fixture known here, so the test does not depend on the order of the others
    await installDriverFromCdn({
      browser: 'chrome',
      version: '120.0.1.2',
      platform: 'win64',
      cdnUrl: baseUrl,
      trust: true,
      destination: path.join(root, 'known-seed', 'chromedriver.exe'),
    });
    const destination = path.join(root, 'known', 'chromedriver.exe');
    await installDriverFromCdn({
      browser: 'chrome',
      version: '120.0.1.2',
      platform: 'win64',
      cdnUrl: baseUrl,
      trust: false,
      destination,
    });
    assert.equal(await fs.readFile(destination, 'utf8'), 'chrome-win');
  });

  it('closes the response when staging fails before the archive is read', async function () {
    let socketClosed: () => void;
    const closed = new Promise<void>((resolve) => (socketClosed = resolve));
    const holding = http.createServer((req, res) => {
      req.socket.once('close', () => socketClosed());
      res.writeHead(200, {'content-length': '1000'});
      res.write('partial');
    });
    await new Promise<void>((resolve) => holding.listen(0, '127.0.0.1', resolve));
    // A regular file as the parent makes creating the staging root fail
    const blocker = path.join(root, 'blocker');
    await fs.writeFile(blocker, 'file');
    const previousTmp = process.env.APPIUM_TMP_DIR;
    process.env.APPIUM_TMP_DIR = path.join(blocker, 'tmp');
    try {
      await assert.rejects(
        installDriverFromCdn({
          browser: 'chrome',
          version: '120.0.1.2',
          platform: 'linux64',
          cdnUrl: `http://127.0.0.1:${(holding.address() as AddressInfo).port}`,
          destination: path.join(root, 'staging-failure', 'chromedriver'),
        }),
      );
      await closed;
    } finally {
      if (previousTmp === undefined) {
        delete process.env.APPIUM_TMP_DIR;
      } else {
        process.env.APPIUM_TMP_DIR = previousTmp;
      }
      holding.closeAllConnections();
      await new Promise((resolve) => holding.close(resolve));
    }
  });

  it('uses the host platform when none is given', async function () {
    const host = await getHostInfo();
    for (const browser of ['chrome', 'edge'] as const) {
      let platform: string;
      try {
        platform = resolvePlatform(browser, host);
      } catch {
        continue; // e.g. Edge on Linux ARM64
      }
      const name = getExecutableName(browser, platform);
      const urlPath =
        browser === 'chrome'
          ? `/7.7.7.7/${platform}/chromedriver-${platform}.zip`
          : `/7.7.7.7/edgedriver_${platform}.zip`;
      archives.set(urlPath, await makeArchive(name, `host-${browser}`));
      const destination = path.join(root, 'host', browser, name);
      await installDriverFromCdn({browser, version: '7.7.7.7', cdnUrl: baseUrl, trust: true, destination});
      assert.equal(await fs.readFile(destination, 'utf8'), `host-${browser}`);
    }
  });

  it('reports a failed download without leaving a file behind', async function () {
    const destination = path.join(root, 'missing', 'msedgedriver.exe');
    await assert.rejects(
      installDriverFromCdn({browser: 'edge', version: '1.2.3.4', platform: 'win64', cdnUrl: baseUrl, destination}),
      /Cannot download the driver archive/,
    );
    assert.equal(await fs.exists(destination), false);
  });

  it('rejects a corrupt archive without leaving a file behind', async function () {
    archives.set('/9.9.9.9/linux64/chromedriver-linux64.zip', Buffer.from('not a zip'));
    const destination = path.join(root, 'corrupt', 'chromedriver');
    await assert.rejects(
      installDriverFromCdn({browser: 'chrome', version: '9.9.9.9', platform: 'linux64', cdnUrl: baseUrl, destination}),
    );
    assert.equal(await fs.exists(destination), false);
  });

  it('rejects versions and platforms that could alter the request path', async function () {
    const base = {browser: 'chrome', cdnUrl: baseUrl, destination: path.join(root, 'x')} as const;
    await assert.rejects(installDriverFromCdn({...base, version: '../../evil'}), /Invalid driver version/);
    await assert.rejects(installDriverFromCdn({...base, version: '1.2?x=1'}), /Invalid driver version/);
    await assert.rejects(
      installDriverFromCdn({...base, version: '1.2.3.4', platform: '../win64'}),
      /Invalid driver platform/,
    );
  });

  it('rejects a malformed CDN URL', async function () {
    await assert.rejects(
      installDriverFromCdn({
        browser: 'chrome',
        version: '120.0.1.2',
        cdnUrl: 'mirror.example.com',
        destination: path.join(root, 'nope'),
      }),
      /Invalid CDN URL/,
    );
  });

  it('rejects a non-http(s) CDN URL', async function () {
    await assert.rejects(
      installDriverFromCdn({
        browser: 'chrome',
        version: '120.0.1.2',
        cdnUrl: 'file:///etc',
        destination: path.join(root, 'nope'),
      }),
      /Unsupported CDN URL protocol/,
    );
  });
});

describe('limitStream', function () {
  const chunks = async function* (...sizes: number[]) {
    for (const size of sizes) {
      yield new Uint8Array(size);
    }
  };
  const collect = async (it: AsyncIterable<Uint8Array>) => {
    let total = 0;
    for await (const chunk of it) {
      total += chunk.byteLength;
    }
    return total;
  };

  it('passes the data through within the limit', async function () {
    assert.equal(await collect(limitStream(chunks(3, 4), {maxBytes: 7, idleMs: 1000, url: 'u'})), 7);
  });

  it('fails when the data exceeds the limit', async function () {
    await assert.rejects(collect(limitStream(chunks(3, 5), {maxBytes: 7, idleMs: 1000, url: 'u'})), /exceeds 7 bytes/);
  });

  it('fails promptly on a stalled response and closes the socket', async function () {
    let socketClosed: () => void;
    const closed = new Promise<void>((resolve) => (socketClosed = resolve));
    const server = http.createServer((req, res) => {
      req.socket.once('close', () => socketClosed());
      // A partial body, then the connection is held open
      res.writeHead(200, {'content-length': '1000'});
      res.write('partial');
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    try {
      const url = `http://127.0.0.1:${(server.address() as AddressInfo).port}/archive.zip`;
      const body = await retrieveData(url, undefined, {timeout: 5000, responseType: 'stream'});
      const started = Date.now();
      await assert.rejects(collect(limitStream(body, {maxBytes: 1000, idleMs: 100, url})), /stalled for 100 ms/);
      assert.ok(Date.now() - started < 2000);
      await closed;
    } finally {
      server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
    }
  });
});

describe('resolvePlatform', function () {
  const cases: [string, HostInfo, DriverBrowser, string | null, string][] = [
    // [title, host, browser, platform (null if unsupported), executable]
    ['Intel Mac', {os: 'mac', arch: 'x64'}, 'chrome', 'mac-x64', 'chromedriver'],
    ['Intel Mac', {os: 'mac', arch: 'x64'}, 'edge', 'mac64', 'msedgedriver'],
    ['Apple Silicon Mac', {os: 'mac', arch: 'arm64'}, 'chrome', 'mac-arm64', 'chromedriver'],
    ['Apple Silicon Mac', {os: 'mac', arch: 'arm64'}, 'edge', 'mac64_m1', 'msedgedriver'],
    ['Windows x86', {os: 'win', arch: 'x86'}, 'chrome', 'win32', 'chromedriver.exe'],
    ['Windows x86', {os: 'win', arch: 'x86'}, 'edge', 'win32', 'msedgedriver.exe'],
    ['Windows x64', {os: 'win', arch: 'x64'}, 'chrome', 'win64', 'chromedriver.exe'],
    ['Windows x64', {os: 'win', arch: 'x64'}, 'edge', 'win64', 'msedgedriver.exe'],
    ['Windows ARM64 (Chrome falls back to x64)', {os: 'win', arch: 'arm64'}, 'chrome', 'win64', 'chromedriver.exe'],
    ['Windows ARM64', {os: 'win', arch: 'arm64'}, 'edge', 'arm64', 'msedgedriver.exe'],
    ['Linux x64', {os: 'linux', arch: 'x64'}, 'chrome', 'linux64', 'chromedriver'],
    ['Linux x64', {os: 'linux', arch: 'x64'}, 'edge', 'linux64', 'msedgedriver'],
    ['Linux ARM64', {os: 'linux', arch: 'arm64'}, 'chrome', 'linux-arm64', 'chromedriver'],
    ['Linux ARM64 (no Edge build)', {os: 'linux', arch: 'arm64'}, 'edge', null, ''],
    ['unknown Linux architecture', {os: 'linux', arch: 'other'}, 'chrome', null, ''],
  ];

  for (const [title, host, browser, platform, executable] of cases) {
    it(`${browser} on ${title}`, function () {
      if (platform === null) {
        assert.throws(() => resolvePlatform(browser, host), /no prebuilt/);
        return;
      }
      assert.equal(resolvePlatform(browser, host), platform);
      assert.equal(getExecutableName(browser, platform), executable);
    });
  }
});

describe('classifyHost', function () {
  const cases: [string, HostFacts, HostInfo][] = [
    [
      'native Windows ARM64, even if the bitness reads 32',
      {os: 'win', processArch: 'arm64', bitness: '32', cpu: 'intel'},
      {os: 'win', arch: 'arm64'},
    ],
    [
      'Windows ARM64 with 64-bit bitness',
      {os: 'win', processArch: 'arm64', bitness: '64', cpu: 'intel'},
      {os: 'win', arch: 'arm64'},
    ],
    ['32-bit Windows', {os: 'win', processArch: 'ia32', bitness: '32', cpu: 'intel'}, {os: 'win', arch: 'x86'}],
    ['Windows x64', {os: 'win', processArch: 'x64', bitness: '64', cpu: 'intel'}, {os: 'win', arch: 'x64'}],
    [
      '32-bit node on 64-bit Windows',
      {os: 'win', processArch: 'ia32', bitness: '64', cpu: 'intel'},
      {os: 'win', arch: 'x64'},
    ],
    ['Apple Silicon Mac', {os: 'mac', processArch: 'arm64', bitness: '64', cpu: 'arm'}, {os: 'mac', arch: 'arm64'}],
    ['Intel Mac', {os: 'mac', processArch: 'x64', bitness: '64', cpu: 'intel'}, {os: 'mac', arch: 'x64'}],
    ['Linux x64', {os: 'linux', processArch: 'x64', bitness: '64', cpu: 'intel'}, {os: 'linux', arch: 'x64'}],
    ['Linux ARM64', {os: 'linux', processArch: 'arm64', bitness: '64', cpu: 'intel'}, {os: 'linux', arch: 'arm64'}],
    [
      'Linux with another architecture',
      {os: 'linux', processArch: 's390x', bitness: '64', cpu: 'intel'},
      {os: 'linux', arch: 'other'},
    ],
  ];

  for (const [title, facts, expected] of cases) {
    it(title, function () {
      assert.deepEqual(classifyHost(facts), expected);
    });
  }
});
