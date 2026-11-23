import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {it} from 'node:test';

import {zip} from '@appium/support';

import {TRUST_STORE_SUFFIX_ENV, assertKnownChromedriver, getTrustStoreDir} from '../../lib/checksums.js';
import {installEdgeDriver} from '../../lib/index.js';

it('installs and trusts only the executable extracted from Microsoft', async (t) => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'edge-install-test-'));
  const previousSuffix = process.env[TRUST_STORE_SUFFIX_ENV];
  process.env[TRUST_STORE_SUFFIX_ENV] = path.basename(root);
  t.after(async () => {
    await rm(getTrustStoreDir(), {recursive: true, force: true});
    if (previousSuffix === undefined) {
      delete process.env[TRUST_STORE_SUFFIX_ENV];
    } else {
      process.env[TRUST_STORE_SUFFIX_ENV] = previousSuffix;
    }
    await rm(root, {recursive: true, force: true});
  });

  const name = process.platform === 'win32' ? 'msedgedriver.exe' : 'msedgedriver';
  const version = '147.0.3179.98';
  const executableDir = path.join(root, 'cache');
  const target = path.join(executableDir, version, name);
  const unknown = path.join(root, 'unknown');
  await mkdir(path.dirname(target), {recursive: true});
  await writeFile(target, 'pre-existing');
  await writeFile(unknown, 'pre-existing');
  const source = path.join(root, 'archive');
  await mkdir(source);
  await writeFile(path.join(source, name), 'downloaded');
  const archive = await zip.toInMemoryZip(source);
  const fetchMock = t.mock.method(globalThis, 'fetch', async (url: string) => {
    if (/^https:\/\/msedgedriver\.microsoft\.com\/LATEST_RELEASE_147_(MACOS|WINDOWS|LINUX)$/.test(url)) {
      return new Response(Buffer.from(`\uFEFF${version}\n`, 'utf16le'));
    }
    assert.match(url, /^https:\/\/msedgedriver\.microsoft\.com\/147\.0\.3179\.98\/edgedriver_\w+\.zip$/);
    return new Response(new Uint8Array(archive));
  });
  assert.equal(await installEdgeDriver({browserVersion: '147.0.3179.85', executableDir}), target);
  assert.equal(fetchMock.mock.callCount(), 2);
  assert.equal(await readFile(target, 'utf8'), 'downloaded');
  await assertKnownChromedriver(target);
  await assert.rejects(assertKnownChromedriver(unknown), /does not match/);
  await writeFile(target, 'replacement');
  await assert.rejects(assertKnownChromedriver(target), /does not match/);
});

it('rejects invalid versions before downloading an archive', async (t) => {
  const fetchMock = t.mock.method(globalThis, 'fetch', async () => new Response('invalid release'));
  const opts = {browserVersion: 'invalid', executableDir: os.tmpdir()};
  await assert.rejects(installEdgeDriver(opts), /Invalid Edge browser version/);
  assert.equal(fetchMock.mock.callCount(), 0);
  await assert.rejects(installEdgeDriver({...opts, browserVersion: '147.0.3179.85'}), /Invalid Edge WebDriver version/);
  assert.equal(fetchMock.mock.callCount(), 1);
});
