import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtemp} from 'node:fs/promises';
import http from 'node:http';
import type {AddressInfo} from 'node:net';
import os from 'node:os';
import path from 'node:path';
import {describe, it, before, after, beforeEach, afterEach} from 'node:test';

import {fs, system, zip} from '@appium/support';

import {ChromedriverStorageClient} from '../../lib/storage-client/storage-client.js';

const IS_WIN = system.isWindows();
const DRIVER_KEY = '120.0.0.0/chromedriver_linux64.zip';

describe('ChromedriverStorageClient driver retrieval', function () {
  let workDir: string;
  let driversDir: string;
  let archive: Buffer;
  let archiveMd5: string;
  let server: http.Server;
  let baseUrl: string;

  before(async function () {
    workDir = await mkdtemp(path.join(os.tmpdir(), 'cd-storage-'));
    const srcDir = path.join(workDir, 'src');
    await fs.mkdir(path.join(srcDir, 'chromedriver-linux64'), {recursive: true});
    await fs.writeFile(
      path.join(srcDir, 'chromedriver-linux64', IS_WIN ? 'chromedriver.exe' : 'chromedriver'),
      'binary',
    );
    await fs.writeFile(path.join(srcDir, 'chromedriver-linux64', 'LICENSE.chromedriver'), 'license');
    const archivePath = path.join(workDir, 'chromedriver.zip');
    await zip.toArchive(archivePath, {cwd: srcDir});
    archive = await fs.readFile(archivePath);
    archiveMd5 = createHash('md5').update(archive).digest('hex');

    server = http.createServer((req, res) => {
      if (req.url === '/driver.zip') {
        res.writeHead(200, {'content-type': 'application/zip'});
        res.end(archive);
      } else if (req.url === '/truncated.zip') {
        // Announce more bytes than are actually sent
        res.writeHead(200, {'content-type': 'application/zip', 'content-length': archive.length});
        res.write(archive.subarray(0, Math.floor(archive.length / 2)));
        setTimeout(() => res.destroy(), 20);
      } else {
        res.writeHead(404);
        res.end('not found');
      }
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });

  after(async function () {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
    await fs.rimraf(workDir);
  });

  beforeEach(async function () {
    driversDir = await mkdtemp(path.join(workDir, 'drivers-'));
  });

  afterEach(async function () {
    await fs.rimraf(driversDir);
  });

  function makeClient(urlPath: string, etag: string | null) {
    const client = new ChromedriverStorageClient({chromedriverDir: driversDir}) as any;
    client.mapping = {[DRIVER_KEY]: {url: `${baseUrl}${urlPath}`, etag, version: '120.0.0.0'}};
    return client;
  }

  it('should stream, extract and install the chromedriver executable', async function () {
    const client = makeClient('/driver.zip', archiveMd5);
    assert.strictEqual(await client.retrieveDriver(DRIVER_KEY, true), true);
    const installed = path.join(driversDir, 'driver_v120.0.0.0' + (IS_WIN ? '.exe' : ''));
    assert.strictEqual(await fs.readFile(installed, 'utf8'), 'binary');
    if (!IS_WIN) {
      assert.strictEqual((await fs.stat(installed)).mode & 0o777, 0o755);
    }
    assert.deepStrictEqual(await fs.readdir(driversDir), [path.basename(installed)]);
  });

  it('should not verify the checksum if it is unknown', async function () {
    assert.strictEqual(await makeClient('/driver.zip', null).retrieveDriver(DRIVER_KEY, true), true);
  });

  it('should not install the executable if the checksum does not match', async function () {
    const client = makeClient('/driver.zip', 'a'.repeat(32));
    await assert.rejects(() => client.retrieveDriver(DRIVER_KEY, true), /checksum .* did not match/);
    assert.deepStrictEqual(await fs.readdir(driversDir), []);
  });

  it('should report download failures', async function () {
    const client = makeClient('/missing.zip', archiveMd5);
    await assert.rejects(() => client.retrieveDriver(DRIVER_KEY, true), /Cannot download chromedriver archive/);
    assert.deepStrictEqual(await fs.readdir(driversDir), []);
  });

  it('should not install anything from an incomplete download', async function () {
    const client = makeClient('/truncated.zip', archiveMd5);
    await assert.rejects(() => client.retrieveDriver(DRIVER_KEY, true));
    assert.deepStrictEqual(await fs.readdir(driversDir), []);
  });

  it('should return false instead of throwing in the non-strict mode', async function () {
    assert.strictEqual(await makeClient('/missing.zip', archiveMd5).retrieveDriver(DRIVER_KEY, false), false);
  });
});
