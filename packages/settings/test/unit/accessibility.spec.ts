import assert from 'node:assert/strict';
import {describe, it, beforeEach, afterEach} from 'node:test';

import {ADB} from 'appium-adb';
import sinon from 'sinon';

import {SettingsApp} from '../../lib/client.js';
import {ACCESSIBILITY_EVENTS_SERVICE} from '../../lib/constants.js';

describe('accessibility events service', function () {
  let sandbox: sinon.SinonSandbox;
  let client: SettingsApp;
  // fake `settings secure` storage
  let store: Record<string, string>;

  beforeEach(function () {
    sandbox = sinon.createSandbox();
    store = {};
    const adb = new ADB({});
    sandbox.stub(adb, 'getSetting').callsFake(async (_ns: string, name: string) => store[name] ?? 'null');
    sandbox.stub(adb, 'setSetting').callsFake(async (_ns: string, name: string, value: string | number) => {
      store[name] = `${value}`;
      return '';
    });
    sandbox.stub(adb, 'shell').callsFake(async (args: string[] | string) => {
      const [cmd, action, , name] = args as string[];
      if (cmd === 'settings' && action === 'delete') {
        delete store[name];
      }
      return '';
    });
    client = new SettingsApp({adb});
  });

  afterEach(function () {
    sandbox.restore();
  });

  it('enables the service when no services are enabled', async function () {
    assert.equal(await client.setAccessibilityEventsServiceState(true), true);
    assert.deepEqual(store, {
      enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE,
      accessibility_enabled: '1',
    });
    assert.equal(await client.isAccessibilityEventsServiceEnabled(), true);
  });

  it('keeps other enabled services', async function () {
    store = {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '1'};
    await client.setAccessibilityEventsServiceState(true);
    assert.equal(store.enabled_accessibility_services, `com.foo/.Svc:${ACCESSIBILITY_EVENTS_SERVICE}`);
    await client.setAccessibilityEventsServiceState(false);
    assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '1'});
  });

  it('is a no-op if the state is already as requested', async function () {
    store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE, accessibility_enabled: '1'};
    assert.equal(await client.setAccessibilityEventsServiceState(true), false);
    store = {};
    assert.equal(await client.setAccessibilityEventsServiceState(false), false);
    assert.deepEqual(store, {});
  });

  it('removes the setting and turns accessibility off if it was the only service', async function () {
    store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE, accessibility_enabled: '1'};
    assert.equal(await client.setAccessibilityEventsServiceState(false), true);
    assert.deepEqual(store, {accessibility_enabled: '0'});
  });

  it('does not report the service as enabled while the global toggle is off', async function () {
    store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE, accessibility_enabled: '0'};
    assert.equal(await client.isAccessibilityEventsServiceEnabled(), false);
  });

  it('switches the global toggle on if the service is listed but accessibility is off', async function () {
    store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE, accessibility_enabled: '0'};
    assert.equal(await client.setAccessibilityEventsServiceState(true), true);
    assert.equal(store.accessibility_enabled, '1');
    assert.equal(store.enabled_accessibility_services, ACCESSIBILITY_EVENTS_SERVICE);
  });

  it('restores the global toggle if the user had accessibility switched off with other services listed', async function () {
    store = {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '0'};
    await client.setAccessibilityEventsServiceState(true);
    assert.equal(store.accessibility_enabled, '1');
    await client.setAccessibilityEventsServiceState(false);
    assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '0'});
  });

  it('leaves the global toggle alone on disabling if it has not been switched on by the helper', async function () {
    store = {
      enabled_accessibility_services: `com.foo/.Svc:${ACCESSIBILITY_EVENTS_SERVICE}`,
      accessibility_enabled: '1',
    };
    await client.setAccessibilityEventsServiceState(false);
    assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '1'});
  });
});
