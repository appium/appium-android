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

  it('should not mutate the original array', function () {
    const args = ['-storepass', 'secret'];
    redactSecrets(args);
    assert.deepEqual(args, ['-storepass', 'secret']);
  });
});
