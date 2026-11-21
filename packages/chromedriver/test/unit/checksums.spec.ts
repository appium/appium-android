import assert from 'node:assert/strict';
import {mkdtemp, writeFile, rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {describe, it, before, after} from 'node:test';

import {
  TRUSTED_CHECKSUMS_FILE,
  assertKnownChromedriver,
  getChromedriverChecksums,
  sha256File,
  trustChecksumOf,
} from '../../lib/checksums.js';
import {Chromedriver} from '../../lib/chromedriver.js';
import {CHROMEDRIVER_CHROME_MAPPING, listFiles} from '../../lib/utils.js';

describe('checksums', function () {
  let dir: string;
  let bin: string;

  before(async function () {
    dir = await mkdtemp(path.join(os.tmpdir(), 'cd-checksums-'));
    bin = path.join(dir, 'chromedriver');
    await writeFile(bin, 'definitely not chromedriver');
  });
  after(async function () {
    await rm(dir, {recursive: true, force: true});
  });

  describe('shipped allowlist', function () {
    it('should only contain known versions and valid sha256 digests', async function () {
      const checksums = await getChromedriverChecksums();
      assert.ok(Object.keys(checksums).length > 0);
      for (const [version, perPlatform] of Object.entries(checksums)) {
        assert.ok(version in CHROMEDRIVER_CHROME_MAPPING, `${version} is missing in mapping.json`);
        assert.ok(Object.keys(perPlatform).length > 0);
        for (const sha of Object.values(perPlatform)) {
          assert.match(sha, /^[0-9a-f]{64}$/);
        }
      }
    });
  });

  describe('assertKnownChromedriver', function () {
    it('should reject an unknown executable', async function () {
      await assert.rejects(assertKnownChromedriver(bin, dir), /does not match any known Chromedriver build/);
    });

    it('should reject a missing file', async function () {
      await assert.rejects(assertKnownChromedriver(path.join(dir, 'nope'), dir), /ENOENT/);
    });

    it('should accept an executable whose checksum was trusted locally', async function () {
      await trustChecksumOf(bin, dir);
      await assertKnownChromedriver(bin, dir);
      assert.ok((await sha256File(bin)).length === 64);
    });

    it('should keep all checksums trusted in parallel', async function () {
      const parallelDir = await mkdtemp(path.join(os.tmpdir(), 'cd-checksums-parallel-'));
      try {
        const files = await Promise.all(
          Array.from({length: 20}, async (_, i) => {
            const f = path.join(parallelDir, `chromedriver-${i}`);
            await writeFile(f, `driver ${i}`);
            return f;
          }),
        );
        await Promise.all(files.map((f) => trustChecksumOf(f, parallelDir)));
        for (const f of files) {
          await assertKnownChromedriver(f, parallelDir);
        }
      } finally {
        await rm(parallelDir, {recursive: true, force: true});
      }
    });

    it('should not accept the locally trusted checksum from another folder', async function () {
      const other = await mkdtemp(path.join(os.tmpdir(), 'cd-checksums-other-'));
      try {
        await assert.rejects(assertKnownChromedriver(bin, other), /does not match/);
      } finally {
        await rm(other, {recursive: true, force: true});
      }
    });

    it('should detect a modified binary', async function () {
      await writeFile(bin, 'definitely not chromedriver, modified');
      await assert.rejects(assertKnownChromedriver(bin, dir), /does not match/);
    });

    it('should keep the trust store out of the listed drivers', async function () {
      assert.ok(!(await listFiles(dir)).some((p) => path.basename(p) === TRUSTED_CHECKSUMS_FILE));
    });
  });

  describe('explicit executable', function () {
    it('should be rejected by default if it is not on the allowlist', async function () {
      const cd = new Chromedriver({executable: bin});
      await assert.rejects((cd as any).initChromedriverPath(), /does not match any known Chromedriver build/);
    });

    it('should be accepted when arbitrary executables are allowed', async function () {
      const cd = new Chromedriver({executable: bin, allowUnverifiedExecutable: true});
      assert.strictEqual(await (cd as any).initChromedriverPath(), bin);
    });
  });
});
