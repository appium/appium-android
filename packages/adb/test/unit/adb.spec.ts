import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {ADB, DEFAULT_ADB_PORT} from '../../lib/adb.js';
import {log} from '../../lib/logger.js';
import {buildInstallArgs} from '../../lib/utils/index.js';

describe('ADB', function () {
  describe('logLevel', function () {
    it('filters each instance without changing the shared logger', function (t) {
      const globalLevel = log.level;
      const debug = t.mock.method(log, 'debug', () => {});
      const warn = t.mock.method(log, 'warn', () => {});
      const error = t.mock.method(log, 'error', () => {});
      const quiet = new ADB({logLevel: 'error'});
      const verbose = new ADB({logLevel: 'debug'});
      const defaultAdb = new ADB();

      quiet.setDeviceId('quiet');
      verbose.setDeviceId('verbose');
      defaultAdb.setDeviceId('default');
      quiet.log.warn('hidden');
      verbose.log.warn('visible');
      quiet.log.error('visible error');

      assert.equal(debug.mock.callCount(), 2);
      assert.deepEqual(
        debug.mock.calls.map(({arguments: args}) => args[0]),
        ['Setting device id to verbose', 'Setting device id to default'],
      );
      assert.equal(warn.mock.callCount(), 1);
      assert.equal(error.mock.callCount(), 1);
      assert.equal(log.level, globalLevel);
      assert.equal(defaultAdb.logLevel, undefined);
    });

    it('preserves and overrides clone levels independently', function (t) {
      const debug = t.mock.method(log, 'debug', () => {});
      const original = new ADB({logLevel: 'error'});
      const clone = original.clone();
      const verbose = original.clone({logLevel: 'debug'});
      assert.equal(clone.logLevel, 'error');
      assert.notEqual(clone.log, original.log);
      clone.logLevel = 'warn';
      original.setDeviceId('original');
      clone.setDeviceId('clone');
      verbose.setDeviceId('verbose');
      assert.equal(original.logLevel, 'error');
      assert.equal(debug.mock.callCount(), 1);
    });

    it('keeps concurrent asynchronous commands isolated', async function (t) {
      const debug = t.mock.method(log, 'debug', () => {});
      const quiet = new ADB({logLevel: 'error'});
      const verbose = new ADB({logLevel: 'debug'});
      t.mock.method(quiet, 'getDeviceProperty', async () => '26');
      t.mock.method(verbose, 'getDeviceProperty', async () => '27');
      assert.deepEqual(await Promise.all([quiet.getApiLevel(), verbose.getApiLevel()]), [26, 27]);
      assert.deepEqual(
        debug.mock.calls.map(({arguments: args}) => args[0]),
        ['Device API level: 27'],
      );
    });

    it('filters helper logs and preserves error throwing', function (t) {
      const debug = t.mock.method(log, 'debug', () => {});
      const error = t.mock.method(log, 'error', () => {});
      const adb = new ADB({logLevel: 'error'});
      buildInstallArgs(22, {grantPermissions: true}, adb.log);
      assert.equal(debug.mock.callCount(), 0);
      const failure = new Error('failure');
      assert.throws(
        () => adb.log.errorAndThrow(failure),
        (err) => err === failure,
      );
      assert.equal(error.mock.callCount(), 1);
    });
  });

  describe('clone', function () {
    it('should copy all options', function () {
      const original = new ADB({
        executable: {path: 'var/adb', defaultArgs: ['-a']},
      });
      const clone = original.clone();

      assert.strictEqual(clone.executable.path, original.executable.path);
      assert.deepStrictEqual(clone.executable.defaultArgs, original.executable.defaultArgs);
    });

    it('should replace specified options', function () {
      const original = new ADB({
        executable: {path: 'adb', defaultArgs: ['-a']},
      });
      const clone = original.clone({
        remoteAdbHost: 'example.com',
      });

      assert.strictEqual(clone.executable.path, original.executable.path);
      assert.deepStrictEqual(clone.executable.defaultArgs, ['-a', '-H', 'example.com', '-P', String(DEFAULT_ADB_PORT)]);
      assert.strictEqual(clone.remoteAdbHost, 'example.com');
      assert.notStrictEqual(clone.adbHost, original.adbHost);
    });

    describe('-a option', function () {
      const portArg = String(DEFAULT_ADB_PORT);
      const REMOTE_HOST = 'example.com';
      const scenarios = [
        {
          name: 'should not have -a option by default',
          originalOptions: {executable: {path: 'adb', defaultArgs: []}},
          cloneOptions: {remoteAdbHost: REMOTE_HOST},
          expectedOriginalArgs: ['-P', portArg],
          expectedCloneArgs: ['-H', REMOTE_HOST, '-P', portArg],
          expectedOriginalListen: false,
          expectedCloneListen: false,
        },
        {
          name: 'should add -a option',
          originalOptions: {executable: {path: 'adb', defaultArgs: []}, listenAllNetwork: true},
          cloneOptions: {remoteAdbHost: REMOTE_HOST},
          expectedOriginalArgs: ['-a', '-P', portArg],
          expectedCloneArgs: ['-a', '-H', REMOTE_HOST, '-P', portArg],
          expectedOriginalListen: true,
          expectedCloneListen: true,
        },
        {
          name: 'should add -a option only for clone',
          originalOptions: {executable: {path: 'adb', defaultArgs: []}},
          cloneOptions: {remoteAdbHost: REMOTE_HOST, listenAllNetwork: true},
          expectedOriginalArgs: ['-P', portArg],
          expectedCloneArgs: ['-a', '-H', REMOTE_HOST, '-P', portArg],
          expectedOriginalListen: false,
          expectedCloneListen: true,
        },
        {
          name: 'should not repeat -a option',
          originalOptions: {executable: {path: 'adb', defaultArgs: ['-a']}},
          cloneOptions: {remoteAdbHost: REMOTE_HOST, listenAllNetwork: true},
          expectedOriginalArgs: ['-a', '-P', portArg],
          expectedCloneArgs: ['-a', '-H', REMOTE_HOST, '-P', portArg],
          expectedOriginalListen: true,
          expectedCloneListen: true,
        },
        {
          name: 'should not add -a option if it was already in the defaultArgs with listenAllNetwork: true',
          originalOptions: {executable: {path: 'adb', defaultArgs: ['-a']}, listenAllNetwork: true},
          cloneOptions: {remoteAdbHost: REMOTE_HOST},
          expectedOriginalArgs: ['-a', '-P', portArg],
          expectedCloneArgs: ['-a', '-H', REMOTE_HOST, '-P', portArg],
          expectedOriginalListen: true,
          expectedCloneListen: true,
        },
        {
          name: 'should listenAllNetwork be true if the given defaultArgs included -a',
          originalOptions: {
            executable: {path: 'adb', defaultArgs: ['-a']},
            listenAllNetwork: false,
          },
          cloneOptions: {remoteAdbHost: REMOTE_HOST},
          expectedOriginalArgs: ['-a', '-P', portArg],
          expectedCloneArgs: ['-a', '-H', REMOTE_HOST, '-P', portArg],
          expectedOriginalListen: true,
          expectedCloneListen: true,
        },
      ];

      scenarios.forEach(
        ({
          name,
          originalOptions,
          cloneOptions,
          expectedOriginalArgs,
          expectedCloneArgs,
          expectedOriginalListen,
          expectedCloneListen,
        }) => {
          it(name, function () {
            const original = new ADB(originalOptions);
            const clone = original.clone(cloneOptions);

            assert.deepStrictEqual(original.executable.defaultArgs, expectedOriginalArgs);
            assert.strictEqual(original.listenAllNetwork, expectedOriginalListen);

            assert.strictEqual(clone.executable.path, original.executable.path);
            assert.deepStrictEqual(clone.executable.defaultArgs, expectedCloneArgs);
            assert.strictEqual(clone.remoteAdbHost, cloneOptions.remoteAdbHost);
            assert.strictEqual(clone.listenAllNetwork, expectedCloneListen);
          });
        },
      );
    });
  });
});
