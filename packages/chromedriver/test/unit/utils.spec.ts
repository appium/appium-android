import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {compareVersions, convertToInt} from '../../lib/utils.js';

describe('utils', function () {
  describe('compareVersions', function () {
    it('should compare numerically rather than lexically', function () {
      assert.ok(compareVersions('2.9', '2.46') < 0);
      assert.ok(compareVersions('114.0.5735.100', '114.0.5735.90') > 0);
    });
    it('should return zero for equal versions', function () {
      assert.strictEqual(compareVersions('114.0.5735.90', '114.0.5735.90'), 0);
    });
    it('should order old and new version formats', function () {
      assert.ok(compareVersions('2.46', '114.0.5735.90') < 0);
    });
    it('should ignore a leading v', function () {
      assert.strictEqual(compareVersions('v2.11', '2.11'), 0);
      assert.ok(compareVersions('v2.11', '2.12') < 0);
    });
    it('should sort a list in ascending order', function () {
      const versions = ['114.0.5735.90', '2.9', '114.0.5735.100', '2.46', '73.0.3683.20'];
      assert.deepStrictEqual(versions.sort(compareVersions), [
        '2.9',
        '2.46',
        '73.0.3683.20',
        '114.0.5735.90',
        '114.0.5735.100',
      ]);
    });
  });

  describe('convertToInt', function () {
    it('should parse a number', function () {
      assert.strictEqual(convertToInt(0), 0);
      assert.strictEqual(convertToInt(1), 1);
      assert.strictEqual(convertToInt(100), 100);
    });
    it('should return null with NaN', function () {
      assert.strictEqual(convertToInt(NaN), null);
    });
    it('should parse a number string', function () {
      assert.strictEqual(convertToInt('0'), 0);
      assert.strictEqual(convertToInt('1.1'), 1);
      assert.strictEqual(convertToInt('-123'), -123);
    });
    it('should return null if non number string is given', function () {
      assert.strictEqual(convertToInt(''), null);
      assert.strictEqual(convertToInt('foo'), null);
    });
    it('should return null if unexpected type', function () {
      assert.strictEqual(convertToInt({}), null);
      assert.strictEqual(convertToInt(null), null);
      assert.strictEqual(convertToInt(true), null);
    });
  });
});
