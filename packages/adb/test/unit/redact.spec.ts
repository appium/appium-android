import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {redactSecrets} from '../../lib/utils/index.js';

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
      '******',
      '--key-pass',
      '******',
      '-storepass',
      '******',
      '-keypass',
      '******',
      '--ks',
      '/path/to/keystore',
    ]);
  });

  it('should mask values in the --name=value form', function () {
    assert.deepEqual(redactSecrets(['--ks-pass=pass:secret', '--ks=a=b']), ['--ks-pass=******', '--ks=a=b']);
  });

  it('should mask lock screen credentials', function () {
    assert.deepEqual(redactSecrets(['shell', 'locksettings', 'set-pin', '--old', '1234', '5678']), [
      'shell',
      'locksettings',
      'set-pin',
      '--old',
      '******',
      '******',
    ]);
    assert.deepEqual(redactSecrets(['shell', 'locksettings', 'verify', '--old', '1234']), [
      'shell',
      'locksettings',
      'verify',
      '--old',
      '******',
    ]);
    assert.deepEqual(redactSecrets(['shell', 'locksettings', 'clear', '--old', '1234']), [
      'shell',
      'locksettings',
      'clear',
      '--old',
      '******',
    ]);
  });

  it('should mask lock screen credentials embedded in a single argument', function () {
    assert.deepEqual(redactSecrets(['shell', 'su', 'root', 'locksettings set-password --old abc def']), [
      'shell',
      'su',
      'root',
      'locksettings set-password ******',
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
