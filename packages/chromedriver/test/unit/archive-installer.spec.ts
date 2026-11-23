import assert from 'node:assert/strict';
import path from 'node:path';
import {Readable} from 'node:stream';
import {it} from 'node:test';

import {fs, tempDir, zip} from '@appium/support';

import {TRUST_STORE_SUFFIX_ENV, assertKnownChromedriver, getTrustStoreDir} from '../../lib/checksums.js';
import {installDriverFromArchive} from '../../lib/index.js';

it('installs and trusts only the executable extracted from the archive', async (t) => {
  const root = await tempDir.openDir();
  const previousSuffix = process.env[TRUST_STORE_SUFFIX_ENV];
  process.env[TRUST_STORE_SUFFIX_ENV] = `driver-install-test-${path.basename(root)}`;
  t.after(async () => {
    await fs.rimraf(getTrustStoreDir());
    if (previousSuffix === undefined) {
      delete process.env[TRUST_STORE_SUFFIX_ENV];
    } else {
      process.env[TRUST_STORE_SUFFIX_ENV] = previousSuffix;
    }
    await fs.rimraf(root);
  });

  const name = 'driver';
  const target = path.join(root, 'cache', 'installed-driver');
  const unknown = path.join(root, 'unknown');
  await fs.mkdirp(path.dirname(target));
  await fs.writeFile(target, 'pre-existing');
  await fs.writeFile(unknown, 'pre-existing');
  const source = path.join(root, 'archive');
  await fs.mkdir(source);
  await fs.mkdir(path.join(source, 'nested'));
  await fs.writeFile(path.join(source, 'nested', name), 'downloaded');
  const archive = await zip.toInMemoryZip(source);
  const opts = {executableName: name, destination: target};
  assert.equal(await installDriverFromArchive({...opts, archive: Readable.from([archive])}), target);
  assert.equal(await fs.readFile(target, 'utf8'), 'downloaded');
  await assertKnownChromedriver(target);
  await assert.rejects(assertKnownChromedriver(unknown), /does not match/);
  await fs.writeFile(target, 'replacement');
  await assert.rejects(assertKnownChromedriver(target), /does not match/);

  await fs.writeFile(path.join(source, name), 'duplicate');
  const ambiguous = await zip.toInMemoryZip(source);
  await assert.rejects(installDriverFromArchive({...opts, archive: Readable.from([ambiguous])}), /found 2/);
  await assert.rejects(
    installDriverFromArchive({...opts, executableName: 'missing', archive: Readable.from([archive])}),
    /found 0/,
  );
  await assert.rejects(installDriverFromArchive({...opts, archive: Readable.from([archive.subarray(0, 12)])}));
  assert.equal(await fs.readFile(target, 'utf8'), 'replacement');
  await assert.rejects(assertKnownChromedriver(path.join(source, name)), /does not match/);
});

it('stages the archive under APPIUM_TMP_DIR and removes staging on failure', async (t) => {
  const root = await tempDir.openDir();
  const previous = process.env.APPIUM_TMP_DIR;
  const temporaryRoot = path.join(root, 'custom-temp');
  process.env.APPIUM_TMP_DIR = temporaryRoot;
  t.after(async () => {
    if (previous === undefined) {
      delete process.env.APPIUM_TMP_DIR;
    } else {
      process.env.APPIUM_TMP_DIR = previous;
    }
    await fs.rimraf(root);
  });

  async function* truncatedArchive() {
    const entries = await fs.readdir(temporaryRoot, {withFileTypes: true});
    assert.equal(entries.length, 1);
    assert.ok(entries[0].isDirectory());
    yield Buffer.from([0x50]);
    throw new Error('archive interrupted');
  }
  await assert.rejects(
    installDriverFromArchive({
      archive: truncatedArchive(),
      executableName: 'driver',
      destination: path.join(root, 'driver'),
    }),
    /archive interrupted/,
  );
  assert.deepEqual(await fs.readdir(temporaryRoot), []);
});
