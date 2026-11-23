import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {Readable} from 'node:stream';
import {it} from 'node:test';

import {zip} from '@appium/support';

import {TRUST_STORE_SUFFIX_ENV, assertKnownChromedriver, getTrustStoreDir} from '../../lib/checksums.js';
import {installDriverFromArchive} from '../../lib/index.js';

it('installs and trusts only the executable extracted from the archive', async (t) => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'driver-install-test-'));
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

  const name = 'driver';
  const target = path.join(root, 'cache', 'installed-driver');
  const unknown = path.join(root, 'unknown');
  await mkdir(path.dirname(target), {recursive: true});
  await writeFile(target, 'pre-existing');
  await writeFile(unknown, 'pre-existing');
  const source = path.join(root, 'archive');
  await mkdir(source);
  await mkdir(path.join(source, 'nested'));
  await writeFile(path.join(source, 'nested', name), 'downloaded');
  const archive = await zip.toInMemoryZip(source);
  const opts = {executableName: name, destination: target};
  assert.equal(await installDriverFromArchive({...opts, archive: Readable.from([archive])}), target);
  assert.equal(await readFile(target, 'utf8'), 'downloaded');
  await assertKnownChromedriver(target);
  await assert.rejects(assertKnownChromedriver(unknown), /does not match/);
  await writeFile(target, 'replacement');
  await assert.rejects(assertKnownChromedriver(target), /does not match/);

  await writeFile(path.join(source, name), 'duplicate');
  const ambiguous = await zip.toInMemoryZip(source);
  await assert.rejects(installDriverFromArchive({...opts, archive: Readable.from([ambiguous])}), /found 2/);
  await assert.rejects(
    installDriverFromArchive({...opts, executableName: 'missing', archive: Readable.from([archive])}),
    /found 0/,
  );
  await assert.rejects(installDriverFromArchive({...opts, archive: Readable.from([archive.subarray(0, 12)])}));
  assert.equal(await readFile(target, 'utf8'), 'replacement');
  await assert.rejects(assertKnownChromedriver(path.join(source, name)), /does not match/);
});
