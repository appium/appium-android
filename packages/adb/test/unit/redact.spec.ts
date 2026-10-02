import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {exec} from 'teen_process';

import {adbExec} from '../../lib/tools/system-calls.js';
import {redactExecError, redactSecrets} from '../../lib/utils/index.js';

describe('redactSecrets', function () {
  it('should mask values of password arguments', function () {
    const args = [
      'sign',
      '--ks-pass',
      'pass:secret',
      '--key-pass',
      'pass:secret2',
      '-storepass',
      'secret3',
      '-keypass',
      'secret4',
      '--ks',
      '/path/to/keystore',
    ];
    assert.deepEqual(redactSecrets(args), [
      'sign',
      '--ks-pass',
      'REDACTED',
      '--key-pass',
      'REDACTED',
      '-storepass',
      'REDACTED',
      '-keypass',
      'REDACTED',
      '--ks',
      '/path/to/keystore',
    ]);
  });

  it('should mask values in the --name=value form', function () {
    assert.deepEqual(redactSecrets(['--ks-pass=pass:secret', '--ks=a=b']), ['--ks-pass=REDACTED', '--ks=a=b']);
  });

  it('should mask lock screen credentials', function () {
    assert.deepEqual(redactSecrets(['shell', 'locksettings', 'set-pin', '--old', '1234', '5678']), [
      'shell',
      'locksettings',
      'set-pin',
      '--old',
      'REDACTED',
      'REDACTED',
    ]);
    assert.deepEqual(redactSecrets(['shell', 'locksettings', 'verify', '--old', '1234']), [
      'shell',
      'locksettings',
      'verify',
      '--old',
      'REDACTED',
    ]);
    assert.deepEqual(redactSecrets(['shell', 'locksettings', 'clear', '--old', '1234']), [
      'shell',
      'locksettings',
      'clear',
      '--old',
      'REDACTED',
    ]);
  });

  it('should mask lock screen credentials embedded in a single argument', function () {
    assert.deepEqual(redactSecrets(['shell', 'su', 'root', 'locksettings set-password --old abc def']), [
      'shell',
      'su',
      'root',
      'locksettings set-password REDACTED',
    ]);
  });

  it('should not mask locksettings commands without credentials', function () {
    const args = ['shell', 'locksettings', 'get-disabled'];
    assert.deepEqual(redactSecrets(args), args);
  });

  it('should not mutate the original array', function () {
    const args = ['-storepass', 'secret'];
    redactSecrets(args);
    assert.deepEqual(args, ['-storepass', 'secret']);
  });
});

describe('redactExecError', function () {
  const failingArgs = (...extra: string[]) => ['-c', 'exit 1', 'sh', ...extra];

  it('should mask keystore passwords in the error message and stack', async function () {
    const args = failingArgs('--ks-pass', 'pass:TOPSECRET1', '--key-pass', 'pass:TOPSECRET2');
    await assert.rejects(exec('/bin/sh', args), (err: Error) => {
      assert.match(err.message, /TOPSECRET1/);
      const redacted = redactExecError(err, '/bin/sh', args);
      assert.strictEqual(redacted, err);
      assert.doesNotMatch(err.message, /TOPSECRET/);
      assert.doesNotMatch(err.stack ?? '', /TOPSECRET/);
      assert.match(err.message, /exited with code 1/);
      return true;
    });
  });

  it('should leave errors without secrets and non-errors untouched', async function () {
    const args = failingArgs('--ks', '/path');
    await assert.rejects(exec('/bin/sh', args), (err: Error) => {
      const before = err.message;
      redactExecError(err, '/bin/sh', args);
      assert.strictEqual(err.message, before);
      return true;
    });
    assert.strictEqual(redactExecError('boom', '/bin/sh', args), 'boom');
  });

  it('should not expose lock credentials in errors thrown by adbExec', async function () {
    const ctx = {
      executable: {path: '/bin/sh', defaultArgs: [] as string[]},
      EXEC_OUTPUT_FORMAT: {STDOUT: 'stdout', FULL: 'full'},
      adbExecTimeout: 5000,
    };
    await assert.rejects(
      adbExec.call(ctx as any, failingArgs('locksettings', 'set-pin', '--old', '1234', '5678')),
      (err: Error) => {
        assert.doesNotMatch(err.message, /1234|5678/);
        assert.doesNotMatch(err.stack ?? '', /1234|5678/);
        assert.match(err.message, /locksettings set-pin --old REDACTED REDACTED/);
        return true;
      },
    );
  });
});
