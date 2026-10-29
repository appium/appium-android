import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {afterEach, beforeEach, describe, it} from 'node:test';
import {promisify} from 'node:util';

import {ADB} from 'appium-adb';
import sinon from 'sinon';

import {SettingsApp} from '../../lib/client.js';
import {MediaProjectionRecorder} from '../../lib/commands/media-projection.js';
import {VideoStreamSession} from '../../lib/commands/video-stream.js';

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

describe('literal shell arguments', {skip: process.platform === 'win32'}, () => {
  let sandbox: sinon.SinonSandbox;
  let adb: ADB;
  let client: SettingsApp;
  beforeEach(() => {
    sandbox = sinon.createSandbox();
    adb = new ADB();
    client = new SettingsApp({adb});
  });
  afterEach(() => sandbox.restore());

  for (const value of [
    `O'Brien "quoted" value`,
    '$APPIUM_QUOTE_TEST*$(printf substitution); echo unexpected',
    'first\nsecond',
    '',
  ]) {
    it(`broadcast preserves literal extras: ${JSON.stringify(value)}`, async () => {
      const shell = sandbox.stub(adb, 'adbExec').resolves('result=-1');
      const args = ['-a', 'io.appium.settings.test', '--es', 'value', value];
      await client.checkBroadcast(args, 'test', false);
      assert.deepEqual(await parse(shell.firstCall.args[0]), ['am', 'broadcast', ...args]);
    });

    it(`editor action preserves literal input: ${JSON.stringify(value)}`, async () => {
      sandbox.stub(adb, 'runInImeContext').callsFake(async (_ime, callback) => await callback());
      const shell = sandbox.stub(adb, 'adbExec').resolves('');
      await client.performEditorAction(value);
      assert.deepEqual(await parse(shell.firstCall.args[0]), ['input', 'text', `/${value}/`]);
    });

    if (value) {
      it(`video resolution remains literal: ${JSON.stringify(value)}`, async () => {
        const session = new VideoStreamSession(adb);
        sandbox.stub(session, 'isRunning').resolves(false);
        const stop = new Error('stop before transport setup');
        const shell = sandbox.stub(adb, 'adbExec').rejects(stop);
        await assert.rejects(session.start({resolution: value}), (err) => err === stop);
        const parsed = await parse(shell.firstCall.args[0]);
        assert.deepEqual(parsed.slice(-3), ['--es', 'resolution', value]);
      });

      it(`recording options remain literal: ${JSON.stringify(value)}`, async () => {
        const recorder = new MediaProjectionRecorder(adb);
        sandbox.stub(recorder, 'isRunning').onFirstCall().resolves(false).onSecondCall().resolves(true);
        sandbox.stub(recorder, 'cleanup').resolves();
        const shell = sandbox.stub(adb, 'adbExec').resolves('');
        await recorder.start({filename: value, resolution: value});
        const parsed = await parse(shell.firstCall.args[0]);
        assert.deepEqual(parsed.slice(-6), ['--es', 'filename', value, '--es', 'resolution', value]);
      });
    }
  }
});
