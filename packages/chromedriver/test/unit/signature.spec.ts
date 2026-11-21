import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {assertSignedEdgeDriver, hasEdgeDriverFileName} from '../../lib/signature.js';

type ExecCall = {cmd: string; args: string[]; opts: any};

function makeExec(result: {stdout?: string} | Error, calls: ExecCall[] = []): any {
  return async (cmd: string, args: string[], opts: any) => {
    calls.push({cmd, args, opts});
    if (result instanceof Error) {
      throw result;
    }
    return {stdout: result.stdout ?? '', stderr: ''};
  };
}

const MICROSOFT_SUBJECT = 'CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US';
const winResult = (info: Record<string, string>) => ({stdout: JSON.stringify(info)});

describe('hasEdgeDriverFileName', function () {
  it('should match Edge WebDriver file names only', function () {
    for (const name of ['msedgedriver', 'msedgedriver.exe', 'MSEdgeDriver.EXE', 'msedgedriver_v157.0.4341.60']) {
      assert.ok(hasEdgeDriverFileName(`/some/dir/${name}`), name);
    }
    for (const name of ['chromedriver', 'chromedriver.exe', 'edgedriver', 'my-msedgedriver']) {
      assert.ok(!hasEdgeDriverFileName(`/some/dir/${name}`), name);
    }
    assert.ok(!hasEdgeDriverFileName('/msedgedriver/chromedriver'));
  });
});

describe('assertSignedEdgeDriver', function () {
  describe('macOS', function () {
    it('should pin the Microsoft team and the driver identifier', async function () {
      const calls: ExecCall[] = [];
      await assertSignedEdgeDriver('/path/msedgedriver', {platform: 'darwin', execFunc: makeExec({}, calls)});
      assert.strictEqual(calls[0].cmd, 'codesign');
      assert.deepStrictEqual(calls[0].args.slice(0, 2), ['--verify', '--strict']);
      assert.match(calls[0].args[2], /^-R=anchor apple generic .*UBF8T346G9.* identifier "msedgedriver"$/);
      assert.strictEqual(calls[0].args[3], '/path/msedgedriver');
    });

    it('should reject if codesign fails', async function () {
      const err = Object.assign(new Error('failed'), {stderr: 'failed to satisfy specified code requirement(s)'});
      await assert.rejects(
        assertSignedEdgeDriver('/path/x', {platform: 'darwin', execFunc: makeExec(err)}),
        /not a valid Microsoft Edge WebDriver one. failed to satisfy/,
      );
    });
  });

  describe('Windows', function () {
    const check = (info: Record<string, string>, calls?: ExecCall[]) =>
      assertSignedEdgeDriver('C:\\x y\\msedgedriver.exe', {
        platform: 'win32',
        execFunc: makeExec(winResult(info), calls),
      });

    it('should accept a valid signature of Microsoft for msedgedriver.exe', async function () {
      const calls: ExecCall[] = [];
      await check({status: 'Valid', subject: MICROSOFT_SUBJECT, original: 'msedgedriver.exe'}, calls);
      // the path is passed via the environment, not interpolated into the script
      assert.strictEqual(calls[0].opts.env.APPIUM_CHROMEDRIVER_VERIFY_PATH, 'C:\\x y\\msedgedriver.exe');
      assert.ok(!calls[0].args.join(' ').includes('x y'));
    });

    it('should reject an invalid signature status', async function () {
      await assert.rejects(
        check({status: 'HashMismatch', subject: MICROSOFT_SUBJECT, original: 'msedgedriver.exe'}),
        /status is 'HashMismatch'/,
      );
      await assert.rejects(check({status: 'NotSigned', subject: '', original: 'msedgedriver.exe'}), /NotSigned/);
    });

    it('should reject a signature of another publisher', async function () {
      await assert.rejects(
        check({status: 'Valid', subject: 'CN=Evil, O=Not Microsoft Corporation Inc', original: 'msedgedriver.exe'}),
        /not signed by Microsoft Corporation/,
      );
    });

    it('should reject another Microsoft-signed binary', async function () {
      await assert.rejects(
        check({status: 'Valid', subject: MICROSOFT_SUBJECT, original: 'cmd.exe'}),
        /is not Microsoft Edge WebDriver/,
      );
    });

    it('should reject if PowerShell fails or prints garbage', async function () {
      await assert.rejects(
        assertSignedEdgeDriver('x', {platform: 'win32', execFunc: makeExec(new Error('no powershell'))}),
        /Cannot verify the Authenticode signature/,
      );
      await assert.rejects(
        assertSignedEdgeDriver('x', {platform: 'win32', execFunc: makeExec({stdout: 'oops'})}),
        /Cannot parse/,
      );
    });
  });

  describe('Linux', function () {
    it('should never verify the signature', async function () {
      const calls: ExecCall[] = [];
      await assert.rejects(
        assertSignedEdgeDriver('/path/msedgedriver', {platform: 'linux', execFunc: makeExec({}, calls)}),
        /cannot be verified on 'linux'/,
      );
      assert.strictEqual(calls.length, 0);
    });
  });

  describe('real codesign', {skip: process.platform !== 'darwin'}, function () {
    it('should reject an Apple signed binary', async function () {
      await assert.rejects(assertSignedEdgeDriver('/bin/ls'), /not a valid Microsoft Edge WebDriver one/);
    });
  });
});
