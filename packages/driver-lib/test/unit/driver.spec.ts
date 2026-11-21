import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {ADB} from 'appium-adb';
import {BaseDriver} from 'appium/driver.js';
import sinon from 'sinon';

import {AndroidDriver, type AndroidDriverOpts, type W3CAndroidDriverCaps} from '../../lib/driver.js';

describe('ADB server host capabilities', function () {
  class DirectBaseDriver extends AndroidDriver {
    override async createSession(w3cCapabilities: W3CAndroidDriverCaps) {
      return await BaseDriver.prototype.createSession.call(this, w3cCapabilities);
    }
  }

  const cases: [string, {'appium:adbHost'?: string; 'appium:remoteAdbHost'?: string}, string | undefined][] = [
    ['adbHost', {'appium:adbHost': 'localhost'}, 'localhost'],
    ['legacy remoteAdbHost', {'appium:remoteAdbHost': 'legacy.example'}, 'legacy.example'],
    ['both names', {'appium:adbHost': 'new.example', 'appium:remoteAdbHost': 'legacy.example'}, 'new.example'],
    ['empty legacy name', {'appium:adbHost': 'new.example', 'appium:remoteAdbHost': ''}, 'new.example'],
    ['empty new name', {'appium:adbHost': '', 'appium:remoteAdbHost': 'legacy.example'}, 'legacy.example'],
    ['both names empty', {'appium:adbHost': '', 'appium:remoteAdbHost': ''}, undefined],
    ['neither name', {}, undefined],
  ];
  for (const [name, caps, expected] of cases) {
    it(`should resolve ${name} consistently after a direct BaseDriver session`, async function () {
      const driver = new DirectBaseDriver();
      const warn = sinon.spy(driver.log, 'warn');
      const createADB = sinon.stub(ADB, 'createADB').resolves(new ADB());
      try {
        const [, sessionCaps] = await driver.createSession({
          alwaysMatch: {platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:adbPort': 5038},
          firstMatch: [caps],
        });
        assert.equal(driver.getAdbHost(), expected);
        await driver.createADB();
        assert.equal(createADB.callCount, 1);
        assert.equal(createADB.firstCall.args[0]?.remoteAdbHost, expected);
        assert.equal(createADB.firstCall.args[0]?.adbPort, 5038);
        assert.equal(sessionCaps.adbHost, caps['appium:adbHost']);
        assert.equal(sessionCaps.remoteAdbHost, caps['appium:remoteAdbHost']);
        assert.equal(driver.opts.adbHost, caps['appium:adbHost']);
        assert.equal(driver.opts.remoteAdbHost, caps['appium:remoteAdbHost']);
        assert.equal(warn.calledWithMatch(/remoteAdbHost.*deprecated/), Boolean(caps['appium:remoteAdbHost']));
      } finally {
        createADB.restore();
        warn.restore();
        await driver.deleteSession();
      }
    });
  }

  it('should prefer a session host to a driver default supplied under the other name', async function () {
    const opts = {adbHost: 'default.example'} as AndroidDriverOpts;
    const driver = new DirectBaseDriver(opts);
    try {
      await driver.createSession({
        alwaysMatch: {
          platformName: 'Android',
          'appium:automationName': 'UiAutomator2',
          'appium:remoteAdbHost': 'session.example',
        },
        firstMatch: [{}],
      });
      assert.equal(driver.getAdbHost(), 'session.example');
      assert.equal(driver.opts.adbHost, 'default.example');
    } finally {
      await driver.deleteSession();
    }
  });

  it('should reject a non-string adbHost before starting the session', async function () {
    const driver = new AndroidDriver();
    await assert.rejects(
      driver.createSession({
        alwaysMatch: {
          platformName: 'Android',
          'appium:automationName': 'UiAutomator2',
          // @ts-expect-error Exercise runtime validation of a malformed client request.
          'appium:adbHost': ['localhost'],
        },
      }),
      /adbHost.*string/i,
    );
    assert.equal(driver.sessionId, null);
  });
});

describe('AndroidDriver inheritance', function () {
  it('should attach commands to the prototype', function () {
    assert.ok(Object.hasOwn(AndroidDriver.prototype, 'mobileSetUiMode'));
    assert.ok(!Object.hasOwn(new AndroidDriver(), 'mobileSetUiMode'));
  });

  it('should allow descendants to override a command and call super', async function () {
    const baseStub = sinon.stub(AndroidDriver.prototype, 'getDeviceTime').resolves('base');
    try {
      class ChildDriver extends AndroidDriver {
        override async getDeviceTime(...args: Parameters<AndroidDriver['getDeviceTime']>) {
          return `child:${await super.getDeviceTime(...args)}`;
        }
      }
      assert.strictEqual(await new ChildDriver().getDeviceTime(), 'child:base');
      assert.ok(baseStub.calledOnce);
    } finally {
      baseStub.restore();
    }
  });

  it('should call the parent implementation in the log overrides', async function () {
    const driver = new AndroidDriver();
    assert.ok((await driver.getLogTypes()).includes('logcat'));
  });
});
