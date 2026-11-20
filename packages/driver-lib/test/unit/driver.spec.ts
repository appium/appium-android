import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import sinon from 'sinon';

import {AndroidDriver} from '../../lib/driver.js';

describe('ADB server host capabilities', function () {
  for (const [name, caps, expected] of [
    ['adbHost', {'appium:adbHost': 'localhost'}, 'localhost'],
    ['legacy remoteAdbHost', {'appium:remoteAdbHost': 'legacy.example'}, 'legacy.example'],
    ['both names', {'appium:adbHost': 'new.example', 'appium:remoteAdbHost': 'legacy.example'}, 'legacy.example'],
    ['neither name', {}, undefined],
  ] as const) {
    it(`should select the ADB host for ${name}`, async function () {
      const driver = new AndroidDriver();
      const [, sessionCaps] = await driver.createSession({
        alwaysMatch: {platformName: 'Android', 'appium:automationName': 'UiAutomator2'},
        firstMatch: [caps],
      });
      try {
        assert.equal(driver.opts.remoteAdbHost, expected);
        assert.equal(sessionCaps.adbHost, expected);
        assert.equal(sessionCaps.remoteAdbHost, expected);
      } finally {
        await driver.deleteSession();
      }
    });
  }

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
