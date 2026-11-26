import assert from 'node:assert/strict';
import http from 'node:http';
import type {AddressInfo} from 'node:net';
import path from 'node:path';
import {after, before, describe, it} from 'node:test';

import {fs, tempDir, zip} from '@appium/support';

import {TRUST_STORE_SUFFIX_ENV, assertKnownChromedriver, getTrustStoreDir} from '../../lib/checksums.js';
import {limitStream} from '../../lib/driver-installer.js';
import {installDriverFromCdn} from '../../lib/index.js';

describe('installDriverFromCdn', function () {
  let root: string;
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
    process.env[TRUST_STORE_SUFFIX_ENV] = `cdn-install-test-${path.basename(root)}`;
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
    delete process.env[TRUST_STORE_SUFFIX_ENV];
    await new Promise((resolve) => server.close(resolve));
    await fs.rimraf(getTrustStoreDir());
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

  it('does not trust a custom CDN by default', async function () {
    const destination = path.join(root, 'untrusted', 'chromedriver');
    await installDriverFromCdn({
      browser: 'chrome',
      version: '120.0.1.2',
      platform: 'linux64',
      cdnUrl: baseUrl,
      destination,
    });
    assert.equal(await fs.readFile(destination, 'utf8'), 'chrome-linux');
    await assert.rejects(assertKnownChromedriver(destination), /does not match any known/);
  });

  it('reports a failed download without leaving a file behind', async function () {
    const destination = path.join(root, 'missing', 'msedgedriver.exe');
    await assert.rejects(
      installDriverFromCdn({browser: 'edge', version: '1.2.3.4', platform: 'win64', cdnUrl: baseUrl, destination}),
      /Cannot download the driver archive/,
    );
    assert.equal(await fs.exists(destination), false);
  });

  it('does not register the checksum when trust is false', async function () {
    const destination = path.join(root, 'untrusted-explicit', 'msedgedriver.exe');
    await installDriverFromCdn({
      browser: 'edge',
      version: '120.0.1.2',
      platform: 'win64',
      cdnUrl: baseUrl,
      trust: false,
      destination,
    });
    await assert.rejects(assertKnownChromedriver(destination), /does not match any known/);
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

  it('fails when the stream stalls and closes the source', async function () {
    let closed = false;
    const stalling = {
      [Symbol.asyncIterator]: () => ({
        next: () => new Promise<IteratorResult<Uint8Array>>(() => {}),
        return: async () => {
          closed = true;
          return {done: true as const, value: undefined};
        },
      }),
    };
    await assert.rejects(collect(limitStream(stalling, {maxBytes: 7, idleMs: 20, url: 'u'})), /stalled for 20 ms/);
    assert.ok(closed);
  });
});
