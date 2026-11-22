import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {access, mkdtemp, writeFile, rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {describe, it, before, after} from 'node:test';
import {promisify} from 'node:util';

import {
  TRUST_STORE_SUFFIX_ENV,
  assertKnownChromedriver,
  getChromedriverChecksums,
  getEdgeDriverChecksums,
  getTrustStoreDir,
  sha256File,
  trustChecksumOf,
} from '../../lib/checksums.js';
import {Chromedriver} from '../../lib/chromedriver.js';
import {CHROMEDRIVER_CHROME_MAPPING} from '../../lib/utils.js';

describe('checksums', function () {
  let dir: string;
  let bin: string;

  before(async function () {
    dir = await mkdtemp(path.join(os.tmpdir(), 'cd-checksums-'));
    bin = path.join(dir, 'chromedriver');
    await writeFile(bin, 'definitely not chromedriver');
    process.env[TRUST_STORE_SUFFIX_ENV] = `test-${path.basename(dir)}`;
  });
  after(async function () {
    await rm(getTrustStoreDir(), {recursive: true, force: true});
    delete process.env[TRUST_STORE_SUFFIX_ENV];
    await rm(dir, {recursive: true, force: true});
  });

  describe('shipped allowlists', function () {
    const assertValid = (checksums: Record<string, Record<string, string>>) => {
      assert.ok(Object.keys(checksums).length > 0);
      for (const perPlatform of Object.values(checksums)) {
        assert.ok(Object.keys(perPlatform).length > 0);
        for (const sha of Object.values(perPlatform)) {
          assert.match(sha, /^[0-9a-f]{64}$/);
        }
      }
    };

    it('should only contain known Chromedriver versions and valid sha256 digests', async function () {
      const checksums = await getChromedriverChecksums();
      assertValid(checksums);
      for (const version of Object.keys(checksums)) {
        assert.ok(version in CHROMEDRIVER_CHROME_MAPPING, `${version} is missing in mapping.json`);
      }
    });

    it('should contain valid Edge WebDriver versions and sha256 digests', async function () {
      const checksums = await getEdgeDriverChecksums();
      assertValid(checksums);
      for (const version of Object.keys(checksums)) {
        assert.match(version, /^\d+(\.\d+){3}$/);
      }
    });
  });

  describe('assertKnownChromedriver', function () {
    it('should reject an unknown executable', async function () {
      await assert.rejects(
        assertKnownChromedriver(bin),
        /does not match any known Chromedriver or Edge WebDriver build/,
      );
    });

    it('should reject a missing file', async function () {
      await assert.rejects(assertKnownChromedriver(path.join(dir, 'nope')), /ENOENT/);
    });

    it('should accept an executable whose checksum was trusted locally', async function () {
      const sha256 = await sha256File(bin);
      await assert.rejects(assertKnownChromedriver(bin), /does not match/);
      await trustChecksumOf(bin);
      await assertKnownChromedriver(bin);
      await access(path.join(getTrustStoreDir(), sha256));
    });

    it('should keep all checksums trusted in parallel', async function () {
      const files = await Promise.all(
        Array.from({length: 20}, async (_, i) => {
          const f = path.join(dir, `parallel-${i}`);
          await writeFile(f, `driver ${i}`);
          return f;
        }),
      );
      await Promise.all(files.map((f) => trustChecksumOf(f)));
      for (const f of files) {
        await assertKnownChromedriver(f);
      }
    });

    it('should keep all checksums trusted when written by multiple processes', async function () {
      const lib = new URL('../../lib/checksums.js', import.meta.url).href;
      const script = `
        import {writeFile} from 'node:fs/promises';
        import path from 'node:path';
        const {trustChecksumOf} = await import(${JSON.stringify(lib)});
        const [prefix, dir] = process.argv.slice(1);
        await Promise.all(Array.from({length: 30}, async (_, i) => {
          const f = path.join(dir, prefix + i);
          await writeFile(f, prefix + i);
          await trustChecksumOf(f);
        }));`;
      const run = (prefix: string) =>
        promisify(execFile)(process.execPath, ['--input-type=module', '-e', script, prefix, dir], {
          env: process.env,
        });
      await Promise.all([run('a-'), run('b-'), run('c-')]);
      for (const prefix of ['a-', 'b-', 'c-']) {
        for (let i = 0; i < 30; i++) {
          await assertKnownChromedriver(path.join(dir, `${prefix}${i}`));
        }
      }
    });

    it('should not accept the locally trusted checksum from another trust store', async function () {
      await trustChecksumOf(bin);
      const suffix = process.env[TRUST_STORE_SUFFIX_ENV];
      process.env[TRUST_STORE_SUFFIX_ENV] = `${suffix}-other`;
      try {
        await assert.rejects(assertKnownChromedriver(bin), /does not match/);
      } finally {
        process.env[TRUST_STORE_SUFFIX_ENV] = suffix;
      }
    });

    it('should detect a modified binary', async function () {
      await writeFile(bin, 'definitely not chromedriver, modified');
      await assert.rejects(assertKnownChromedriver(bin), /does not match/);
    });

    it('should not take trusted checksums from the executable folder', async function () {
      const driversDir = await mkdtemp(path.join(os.tmpdir(), 'cd-checksums-session-'));
      try {
        const exe = path.join(driversDir, 'chromedriver');
        await writeFile(exe, 'client-provided driver');
        // a client could prepare any trust data in the folder passed with the capability
        await writeFile(path.join(driversDir, await sha256File(exe)), '');
        await writeFile(path.join(driversDir, '.trusted-checksums.json'), JSON.stringify([await sha256File(exe)]));
        const cd = new Chromedriver({executable: exe, executableDir: driversDir});
        await assert.rejects((cd as any).initChromedriverPath(), /does not match/);
      } finally {
        await rm(driversDir, {recursive: true, force: true});
      }
    });
  });

  describe('explicit executable', function () {
    it('should be rejected by default if it is not on the allowlist', async function () {
      const cd = new Chromedriver({executable: bin});
      await assert.rejects(
        (cd as any).initChromedriverPath(),
        /does not match any known Chromedriver or Edge WebDriver build/,
      );
    });

    it('should be accepted when arbitrary executables are allowed', async function () {
      const cd = new Chromedriver({executable: bin, allowUnverifiedExecutable: true});
      assert.strictEqual(await (cd as any).initChromedriverPath(), bin);
    });
  });

  describe('executables folder', function () {
    it('should verify every executable while listing the folder', async function () {
      const driversDir = await mkdtemp(path.join(os.tmpdir(), 'cd-checksums-list-'));
      try {
        const known = Array.from({length: 9}, (_, i) => path.join(driversDir, `chromedriver_known_${i}`));
        const unknown = path.join(driversDir, 'chromedriver_unknown');
        for (const f of known) {
          await writeFile(f, `driver ${f}`);
          await trustChecksumOf(f);
        }
        await writeFile(unknown, 'unknown driver');

        const cd = new Chromedriver({executableDir: driversDir});
        const probed: string[] = [];
        cd._execFunc = (async (executable: string) => {
          probed.push(executable);
          return {stdout: 'ChromeDriver 120.0.6099.109', stderr: ''};
        }) as any;
        const found = await (cd as any).getChromedrivers({});

        assert.deepStrictEqual(found.map((x: {executable: string}) => x.executable).sort(), known.sort());
        // the unknown executable must never be run
        assert.ok(!probed.includes(unknown));
      } finally {
        await rm(driversDir, {recursive: true, force: true});
      }
    });
  });

  describe('executable replaced after verification', function () {
    it('should be rejected on the next launch', async function () {
      const driversDir = await mkdtemp(path.join(os.tmpdir(), 'cd-checksums-swap-'));
      try {
        const exe = path.join(driversDir, 'chromedriver');
        await writeFile(exe, 'trusted driver');
        await trustChecksumOf(exe);
        const cd = new Chromedriver({executable: exe, executableDir: driversDir});
        assert.strictEqual(await (cd as any).initChromedriverPath(), exe);

        await writeFile(exe, 'malicious replacement');
        await assert.rejects(
          (cd as any).initChromedriverPath(),
          /does not match any known Chromedriver or Edge WebDriver build/,
        );
      } finally {
        await rm(driversDir, {recursive: true, force: true});
      }
    });
  });
});
