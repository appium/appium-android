import assert from 'node:assert/strict';
import {describe, it, beforeEach, afterEach} from 'node:test';

import {ADB} from 'appium-adb';
import sinon from 'sinon';

import {SettingsApp} from '../../lib/client.js';
import {ACCESSIBILITY_EVENTS_SERVICE} from '../../lib/constants.js';

describe('accessibility events service', function () {
  let sandbox: sinon.SinonSandbox;
  let client: SettingsApp;
  let getSetting: sinon.SinonStub;
  let setSetting: sinon.SinonStub;
  let shell: sinon.SinonStub;

  beforeEach(function () {
    sandbox = sinon.createSandbox();
    const adb = new ADB({});
    getSetting = sandbox.stub(adb, 'getSetting');
    setSetting = sandbox.stub(adb, 'setSetting').resolves('');
    shell = sandbox.stub(adb, 'shell').resolves('');
    client = new SettingsApp({adb});
  });

  afterEach(function () {
    sandbox.restore();
  });

  it('enables the service when no services are enabled', async function () {
    getSetting.resolves('null\n');
    assert.equal(await client.setAccessibilityEventsServiceState(true), true);
    assert.deepEqual(setSetting.firstCall.args, [
      'secure',
      'enabled_accessibility_services',
      ACCESSIBILITY_EVENTS_SERVICE,
    ]);
    assert.deepEqual(setSetting.secondCall.args, ['secure', 'accessibility_enabled', 1]);
  });

  it('keeps other enabled services', async function () {
    getSetting.resolves('com.foo/.Svc');
    await client.setAccessibilityEventsServiceState(true);
    assert.equal(setSetting.firstCall.args[2], `com.foo/.Svc:${ACCESSIBILITY_EVENTS_SERVICE}`);
  });

  it('is a no-op if the state is already as requested', async function () {
    getSetting.resolves(ACCESSIBILITY_EVENTS_SERVICE);
    assert.equal(await client.setAccessibilityEventsServiceState(true), false);
    getSetting.resolves('null');
    assert.equal(await client.setAccessibilityEventsServiceState(false), false);
    assert.equal(setSetting.called, false);
  });

  it('removes the setting and turns accessibility off if it was the only service', async function () {
    getSetting.resolves(ACCESSIBILITY_EVENTS_SERVICE);
    assert.equal(await client.setAccessibilityEventsServiceState(false), true);
    assert.deepEqual(shell.firstCall.args[0], ['settings', 'delete', 'secure', 'enabled_accessibility_services']);
    assert.deepEqual(setSetting.firstCall.args, ['secure', 'accessibility_enabled', 0]);
  });

  it('only removes its own entry when other services are enabled', async function () {
    getSetting.resolves(`com.foo/.Svc:${ACCESSIBILITY_EVENTS_SERVICE}`);
    await client.setAccessibilityEventsServiceState(false);
    assert.equal(setSetting.firstCall.args[2], 'com.foo/.Svc');
    assert.equal(setSetting.secondCall.args[2], 1);
  });
});
