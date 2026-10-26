import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {afterEach, beforeEach, describe, it} from 'node:test';
import {promisify} from 'node:util';

import sinon from 'sinon';

import {ADB} from '../../lib/adb.js';

const exec = promisify(execFile);

describe('literal shell API', () => {
  let adb: ADB;
  let sandbox: sinon.SinonSandbox;
  beforeEach(() => {
    adb = new ADB();
    sandbox = sinon.createSandbox();
  });
  afterEach(() => sandbox.restore());

  it('forwards execution options and preserves full output', async () => {
    const result = {stdout: 'output', stderr: 'warning'};
    const adbExec = sandbox.stub(adb, 'adbExec').resolves(result as any);
    const options = {outputFormat: 'full' as const, timeout: 1234, exclusive: true};
    assert.equal(await adb.shellArgs(['printf', 'hello'], options), result);
    assert.deepEqual(adbExec.firstCall.args, [['shell', 'printf', 'hello'], options]);
  });

  it('propagates execution errors', async () => {
    const error = new Error('command failed');
    sandbox.stub(adb, 'shell').rejects(error);
    await assert.rejects(adb.shellArgs(['false']), (actual) => actual === error);
  });

  it('does not modify caller-owned argument arrays', async () => {
    sandbox.stub(adb, 'shell').resolves('');
    const args = Object.freeze(['printf', 'a b', ';']);
    await adb.shellArgs(args);
    // The batch transformer may reuse its array; separator insertion must not mutate it.
    const batch = ['printf', 'a b', ';'];
    await adb.shellArgsChunks(() => batch, ['first', 'second']);
    assert.deepEqual(batch, ['printf', 'a b', ';']);
  });

  it('retains raw shell syntax for existing callers', async () => {
    const adbExec = sandbox.stub(adb, 'adbExec').resolves('');
    const command = 'printf hello | cat > /dev/null';
    await adb.shell(command);
    assert.deepEqual(adbExec.firstCall.args[0], ['shell', command]);
    await adb.shellChunks(() => ['printf', 'hello', ';'], ['value']);
    assert.deepEqual(adbExec.secondCall.args[0], ['shell', 'printf', 'hello', ';']);
  });

  it('rejects an invalid batch transformer before executing', async () => {
    const shell = sandbox.stub(adb, 'shell');
    await assert.rejects(
      adb.shellArgsChunks(() => null as any, ['value']),
      /must result in an array/,
    );
    assert.equal(shell.called, false);
  });

  it('quotes empty strings and operators exactly once', {skip: process.platform === 'win32'}, async () => {
    const shell = sandbox.stub(adb, 'shell').resolves('');
    const args = ['printf', '', ';', '|', '>', '*', `O'Brien`, '$HOME', '`printf changed`', 'a\nb'];
    await adb.shellArgs(args);
    const command = (shell.firstCall.args[0] as string[]).join(' ');
    const {stdout} = await exec('/bin/sh', ['-c', `set -- ${command}; printf '%s\\0' "$@"`]);
    assert.deepEqual(stdout.split('\0').slice(0, -1), args);
  });

  it('preserves literal arguments through privileged execution', {skip: process.platform === 'win32'}, async () => {
    sandbox.stub(adb, 'isRoot').resolves(false);
    const adbExec = sandbox.stub(adb, 'adbExec').resolves('');
    const args = ['printf', `O'Brien $HOME;`, ''];
    await adb.shellArgs(args, {privileged: true});
    const command = (adbExec.firstCall.args[0] as string[]).slice(1).join(' ');
    // Model the outer device shell and the inner command interpreted by su.
    const {stdout} = await exec('/bin/sh', ['-c', `set -- ${command}; printf '%s\\0' "$@"`]);
    const outerArgs = stdout.split('\0').slice(0, -1);
    assert.deepEqual(outerArgs.slice(0, 2), ['su', 'root']);
    const inner = await exec('/bin/sh', ['-c', `set -- ${outerArgs.slice(2).join(' ')}; printf '%s\\0' "$@"`]);
    assert.deepEqual(inner.stdout.split('\0').slice(0, -1), args);
  });

  it(
    'splits batches after quoting and preserves a literal trailing semicolon',
    {skip: process.platform === 'win32'},
    async () => {
      const shell = sandbox.stub(adb, 'shell').resolves('');
      const values = Array.from({length: 6}, (_, i) => `${i}${' '.repeat(180)}`);
      await adb.shellArgsChunks((value) => ['capture', value, ';'], values);
      assert.ok(shell.callCount > 1);
      const actual: string[] = [];
      for (const call of shell.getCalls()) {
        const command = (call.args[0] as string[]).join(' ');
        assert.ok(command.length < 1000);
        const {stdout} = await exec('/bin/sh', ['-c', `capture() { printf '%s\\0' "$@"; }; ${command}`]);
        actual.push(...stdout.split('\0').slice(0, -1));
      }
      assert.deepEqual(
        actual,
        values.flatMap((value) => [value, ';']),
      );
    },
  );
});
