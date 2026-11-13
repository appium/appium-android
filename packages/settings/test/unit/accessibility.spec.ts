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

  it('removes the setting and restores the global toggle if it was off before enabling', async function () {
    await client.setAccessibilityEventsServiceState(true);
    assert.equal(await client.setAccessibilityEventsServiceState(false), true);
    assert.deepEqual(store, {accessibility_enabled: '0'});
  });

  it('leaves the global toggle on if it was not switched on by the helper and it was the only service', async function () {
    store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE, accessibility_enabled: '1'};
    assert.equal(await client.setAccessibilityEventsServiceState(false), true);
    assert.deepEqual(store, {accessibility_enabled: '1'});
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

  it('recognizes the fully qualified component name', async function () {
    const flat = 'io.appium.settings/io.appium.settings.AccessibilityEventsService';
    store = {enabled_accessibility_services: `com.foo/.Svc:${flat}`, accessibility_enabled: '1'};
    assert.equal(await client.isAccessibilityEventsServiceEnabled(), true);
    assert.equal(await client.setAccessibilityEventsServiceState(true), false);
    assert.equal(await client.setAccessibilityEventsServiceState(false), true);
    assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '1'});
  });

  it('keeps the global toggle on if another service has been enabled since the helper switched it on', async function () {
    await client.setAccessibilityEventsServiceState(true);
    store.enabled_accessibility_services += ':com.foo/.Svc';
    await client.setAccessibilityEventsServiceState(false);
    assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '1'});
  });

  describe('rollback on failure', function () {
    function failToggleWrite() {
      (client.adb.setSetting as sinon.SinonStub)
        .withArgs('secure', 'accessibility_enabled')
        .rejects(new Error('denied'));
    }

    it('removes the added service if switching the global toggle on fails', async function () {
      failToggleWrite();
      await assert.rejects(client.setAccessibilityEventsServiceState(true), /denied/);
      assert.deepEqual(store, {});
    });

    it('restores the previous services list if switching the global toggle on fails', async function () {
      store = {enabled_accessibility_services: 'com.foo/.Svc'};
      failToggleWrite();
      await assert.rejects(client.setAccessibilityEventsServiceState(true), /denied/);
      assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc'});
    });

    it('does not touch the services list if the service was already listed', async function () {
      store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE};
      failToggleWrite();
      await assert.rejects(client.setAccessibilityEventsServiceState(true), /denied/);
      assert.deepEqual(store, {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE});
    });

    it('does not remember switching the global toggle on after a failure', async function () {
      failToggleWrite();
      await assert.rejects(client.setAccessibilityEventsServiceState(true), /denied/);
      store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE, accessibility_enabled: '1'};
      await client.setAccessibilityEventsServiceState(false);
      assert.deepEqual(store, {accessibility_enabled: '1'});
    });

    it('rethrows the original error if the rollback fails too', async function () {
      failToggleWrite();
      (client.adb.shell as sinon.SinonStub).rejects(new Error('rollback failed'));
      await assert.rejects(client.setAccessibilityEventsServiceState(true), /denied/);
    });
  });

  describe('pending toggle restoration', function () {
    // Fails the first write of `accessibility_enabled=0` only
    function failFirstToggleRestoration() {
      let failed = false;
      (client.adb.setSetting as sinon.SinonStub).callsFake(
        async (_ns: string, name: string, value: string | number) => {
          if (name === 'accessibility_enabled' && `${value}` === '0' && !failed) {
            failed = true;
            throw new Error('denied');
          }
          store[name] = `${value}`;
          return '';
        },
      );
    }

    it('retries the toggle restoration even though the service is no longer listed', async function () {
      await client.setAccessibilityEventsServiceState(true);
      failFirstToggleRestoration();
      await assert.rejects(client.setAccessibilityEventsServiceState(false), /denied/);
      assert.deepEqual(store, {accessibility_enabled: '1'});

      assert.equal(await client.setAccessibilityEventsServiceState(false), true);
      assert.deepEqual(store, {accessibility_enabled: '0'});
      // the marker is cleared after a successful restoration
      assert.equal(await client.setAccessibilityEventsServiceState(false), false);
    });

    it('retries the restoration if other services have been listed before enabling', async function () {
      store = {enabled_accessibility_services: 'com.foo/.Svc'};
      await client.setAccessibilityEventsServiceState(true);
      failFirstToggleRestoration();
      await assert.rejects(client.setAccessibilityEventsServiceState(false), /denied/);
      assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '1'});

      assert.equal(await client.setAccessibilityEventsServiceState(false), true);
      assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '0'});
    });

    it('drops the pending restoration if another service has been enabled before the retry', async function () {
      await client.setAccessibilityEventsServiceState(true);
      failFirstToggleRestoration();
      await assert.rejects(client.setAccessibilityEventsServiceState(false), /denied/);
      store.enabled_accessibility_services = 'com.foo/.Svc';

      assert.equal(await client.setAccessibilityEventsServiceState(false), false);
      assert.deepEqual(store, {enabled_accessibility_services: 'com.foo/.Svc', accessibility_enabled: '1'});
      // the stale marker must not switch the toggle off later
      await client.setAccessibilityEventsServiceState(true);
      await client.setAccessibilityEventsServiceState(false);
      assert.equal(store.accessibility_enabled, '1');
    });

    it('drops the pending restoration if a failed enabling has seen the toggle switched off', async function () {
      await client.setAccessibilityEventsServiceState(true);
      failFirstToggleRestoration();
      await assert.rejects(client.setAccessibilityEventsServiceState(false), /denied/);
      // the user switches accessibility off, then switching it on by the helper fails
      store.accessibility_enabled = '0';
      (client.adb.setSetting as sinon.SinonStub).callsFake(
        async (_ns: string, name: string, value: string | number) => {
          if (name === 'accessibility_enabled') {
            throw new Error('denied');
          }
          store[name] = `${value}`;
          return '';
        },
      );
      await assert.rejects(client.setAccessibilityEventsServiceState(true), /denied/);

      // the user switches accessibility on again, so the stale marker must not switch it off
      store = {enabled_accessibility_services: ACCESSIBILITY_EVENTS_SERVICE, accessibility_enabled: '1'};
      await client.setAccessibilityEventsServiceState(false);
      assert.equal(store.accessibility_enabled, '1');
    });

    it('keeps the pending restoration if a failed enabling has seen the toggle still on', async function () {
      await client.setAccessibilityEventsServiceState(true);
      failFirstToggleRestoration();
      await assert.rejects(client.setAccessibilityEventsServiceState(false), /denied/);
      (client.adb.setSetting as sinon.SinonStub).callsFake(
        async (_ns: string, name: string, value: string | number) => {
          if (name === 'enabled_accessibility_services') {
            throw new Error('denied');
          }
          store[name] = `${value}`;
          return '';
        },
      );
      await assert.rejects(client.setAccessibilityEventsServiceState(true), /denied/);

      (client.adb.setSetting as sinon.SinonStub).callsFake(
        async (_ns: string, name: string, value: string | number) => {
          store[name] = `${value}`;
          return '';
        },
      );
      assert.equal(await client.setAccessibilityEventsServiceState(false), true);
      assert.equal(store.accessibility_enabled, '0');
    });

    it('keeps the pending restoration if removing the service fails', async function () {
      await client.setAccessibilityEventsServiceState(true);
      (client.adb.shell as sinon.SinonStub).rejects(new Error('shell failed'));
      await assert.rejects(client.setAccessibilityEventsServiceState(false), /shell failed/);
      (client.adb.shell as sinon.SinonStub).callsFake(async (args: string[]) => {
        delete store[args[3]];
        return '';
      });
      assert.equal(await client.setAccessibilityEventsServiceState(false), true);
      assert.deepEqual(store, {accessibility_enabled: '0'});
    });
  });
});
