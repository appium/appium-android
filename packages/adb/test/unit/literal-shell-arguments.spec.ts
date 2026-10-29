import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {afterEach, beforeEach, describe, it} from 'node:test';
import {promisify} from 'node:util';

import sinon from 'sinon';

import {ADB} from '../../lib/adb.js';

// Exercise POSIX parsing, rather than computing expectations with the quoting helper.
async function parse(command: string | string[]): Promise<string[]> {
  if (Array.isArray(command) && command[0] === 'shell') {
    command = command.slice(1);
  }
  const {stdout} = await promisify(execFile)(
    '/bin/sh',
    ['-c', `set -- ${Array.isArray(command) ? command.join(' ') : command}; printf '%s\\0' "$@"`],
    {env: {...process.env, APPIUM_QUOTE_TEST: 'expanded'}},
  );
  return stdout.split('\0').slice(0, -1);
}

const values = [
  `com.example O'Brien "quoted"`,
  'com.example.$APPIUM_QUOTE_TEST*$(printf substitution); echo unexpected',
  'com.example\nsecond-line',
];

describe('literal shell arguments', {skip: process.platform === 'win32'}, () => {
  let adb: ADB;
  let sandbox: sinon.SinonSandbox;
  beforeEach(() => {
    adb = new ADB();
    sandbox = sinon.createSandbox();
  });
  afterEach(() => sandbox.restore());

  for (const value of values) {
    it(`startApp quotes builder output once: ${JSON.stringify(value)}`, async () => {
      sandbox.stub(adb, 'getApiLevel').resolves(30);
      const shell = sandbox.stub(adb, 'adbExec').resolves('');
      await adb.startApp({
        pkg: 'com.example',
        activity: '.Main$Inner',
        action: value,
        category: value,
        stopApp: false,
        waitForLaunch: false,
      });
      assert.deepEqual(await parse(shell.firstCall.args[0]), [
        'am',
        'start-activity',
        '-n',
        'com.example/.Main$Inner',
        '-a',
        value,
        '-c',
        value,
      ]);
    });

    it(`settings arguments remain literal: ${JSON.stringify(value)}`, async () => {
      const shell = sandbox.stub(adb, 'adbExec').resolves('');
      await adb.setSetting(value, value, value);
      assert.deepEqual(await parse(shell.firstCall.args[0]), ['settings', 'put', value, value, value]);
    });

    for (const method of ['grantPermission', 'revokePermission'] as const) {
      it(`${method} preserves package and permission: ${JSON.stringify(value)}`, async () => {
        const shell = sandbox.stub(adb, 'adbExec').resolves('');
        await adb[method](value, value);
        assert.deepEqual(await parse(shell.firstCall.args[0]), [
          'pm',
          method === 'grantPermission' ? 'grant' : 'revoke',
          value,
          value,
        ]);
      });
    }

    it(`batched grants preserve data and command separators: ${JSON.stringify(value)}`, async () => {
      const shell = sandbox.stub(adb, 'adbExec').resolves('');
      const permissions = [value, 'android.permission.CAMERA'];
      await adb.grantPermissions(value, permissions);
      const args = (shell.firstCall.args[0] as string[]).slice(1);
      const {stdout} = await promisify(execFile)(
        '/bin/sh',
        ['-c', `pm() { printf '%s\\0' "$@"; }; ${args.join(' ')}`],
        {env: {...process.env, APPIUM_QUOTE_TEST: 'expanded'}},
      );
      assert.deepEqual(
        stdout.split('\0').slice(0, -1),
        permissions.flatMap((p) => ['grant', value, p]),
      );
    });

    for (const [method, prefix] of [
      ['killPackage', ['am', 'kill']],
      ['clear', ['pm', 'clear']],
      ['getGrantedPermissions', ['dumpsys', 'package']],
      ['getDeniedPermissions', ['dumpsys', 'package']],
      ['getReqPermissions', ['dumpsys', 'package']],
      ['getPackageInfo', ['dumpsys', 'package']],
      ['targetSdkVersionUsingPKG', ['dumpsys', 'package']],
    ] as const) {
      it(`${method} preserves package data: ${JSON.stringify(value)}`, async () => {
        const shell = sandbox.stub(adb, 'adbExec').resolves('targetSdk=30');
        await adb[method](value);
        assert.deepEqual(await parse(shell.firstCall.args[0]), [...prefix, value]);
      });
    }

    for (const method of ['ls', 'rimraf', 'installFromDevicePath'] as const) {
      it(`${method} preserves paths: ${JSON.stringify(value)}`, async () => {
        const shell = sandbox.stub(adb, 'adbExec').resolves('Success');
        const path = `/data/local/tmp/${value}.apk`;
        await adb[method](path);
        const prefix = method === 'ls' ? ['ls'] : method === 'rimraf' ? ['rm', '-rf'] : ['pm', 'install', '-r'];
        assert.deepEqual(await parse(shell.firstCall.args[0]), [...prefix, path]);
      });
    }

    it(`package lookup preserves user and package data: ${JSON.stringify(value)}`, async () => {
      sandbox.stub(adb, 'getApiLevel').resolves(25);
      const shell = sandbox.stub(adb, 'adbExec').resolves('package:/base.apk');
      assert.equal(await adb.isAppInstalled(value, {user: value}), true);
      assert.deepEqual(await parse(shell.firstCall.args[0]), ['pm', 'path', '--user', value, value]);
    });

    it(`activity resolution preserves package data: ${JSON.stringify(value)}`, async () => {
      sandbox.stub(adb, 'getApiLevel').resolves(30);
      const shell = sandbox.stub(adb, 'adbExec').resolves({stdout: 'com.example/.Main', stderr: ''} as any);
      await adb.resolveLaunchableActivity(value);
      assert.deepEqual(await parse(shell.firstCall.args[0]), ['cmd', 'package', 'resolve-activity', '--brief', value]);
    });
  }
});
