import assert from 'node:assert/strict';
import {describe, it, beforeEach, afterEach} from 'node:test';

import {ADB} from 'appium-adb';
import sinon from 'sinon';

import {adjustTimeZone, getDeviceTime} from '../../../lib/commands/time.js';
import {AndroidDriver} from '../../../lib/driver.js';

describe('Time Commands', function () {
  let driver: AndroidDriver;
  const sandbox = sinon.createSandbox();

  beforeEach(function () {
    const adb = new ADB();
    driver = new AndroidDriver();
    driver.adb = adb;
  });
  afterEach(function () {
    sandbox.verifyAndRestore();
  });

  describe('getDeviceTime', function () {
    it('return formatted device time with timezone offset', async function () {
      sandbox.stub(driver.adb, 'shell').withArgs(['date', '+%Y-%m-%dT%T%z']).resolves('2026-07-08T15:30:45+0200\n');

      const result = await getDeviceTime.call(driver, 'YYYY-MM-DD HH:mm:ss Z');

      assert.strictEqual(result, '2026-07-08 15:30:45 +02:00');
    });

    it('use the default format when no format is provided', async function () {
      sandbox.stub(driver.adb, 'shell').withArgs(['date', '+%Y-%m-%dT%T%z']).resolves('2026-07-08T15:30:45+0200');

      const result = await getDeviceTime.call(driver);

      assert.strictEqual(result, '2026-07-08T15:30:45+02:00');
    });

    it('return the original timestamp when parsing fails', async function () {
      sandbox.stub(driver.adb, 'shell').withArgs(['date', '+%Y-%m-%dT%T%z']).resolves('invalid-date');

      const result = await getDeviceTime.call(driver, 'YYYY-MM-DD HH:mm:ss');

      assert.strictEqual(result, 'invalid-date');
    });
  });

  describe('adjustTimeZone', function () {
    it('sets canonical IANA time zones verbatim', async function () {
      const shellStub = sandbox.stub(driver.adb, 'shell').resolves('');
      const getTimeZoneStub = sandbox.stub(driver.adb, 'getTimeZone');
      const warnSpy = sandbox.spy(driver.log, 'warn');

      for (const zone of ['America/New_York', 'Europe/London', 'Africa/Dar_es_Salaam', 'America/Port-au-Prince']) {
        shellStub.resetHistory();
        getTimeZoneStub.resetHistory();
        getTimeZoneStub.resolves(zone);
        await adjustTimeZone.call(driver, zone);
        sinon.assert.calledOnceWithExactly(shellStub, ['service', 'call', 'alarm', '3', 's16', zone]);
        sinon.assert.calledOnce(getTimeZoneStub);
      }
      assert.strictEqual(warnSpy.callCount, 0);
    });

    it('accepts UTC, GMT, and Etc/GMT time zones omitted by Intl.supportedValuesOf', async function () {
      const shellStub = sandbox.stub(driver.adb, 'shell').resolves('');
      const getTimeZoneStub = sandbox.stub(driver.adb, 'getTimeZone');
      const warnSpy = sandbox.spy(driver.log, 'warn');

      for (const zone of ['UTC', 'Etc/UTC', 'GMT', 'Etc/GMT', 'Etc/GMT+5', 'Etc/GMT-8']) {
        shellStub.resetHistory();
        getTimeZoneStub.resetHistory();
        getTimeZoneStub.resolves(zone);
        await adjustTimeZone.call(driver, zone);
        sinon.assert.calledOnceWithExactly(shellStub, ['service', 'call', 'alarm', '3', 's16', zone]);
        sinon.assert.calledOnce(getTimeZoneStub);
      }
      assert.strictEqual(warnSpy.callCount, 0);
    });

    it('accepts modern canonical IANA names and aliases omitted by Intl.supportedValuesOf', async function () {
      const shellStub = sandbox.stub(driver.adb, 'shell').resolves('');
      const getTimeZoneStub = sandbox.stub(driver.adb, 'getTimeZone');
      const warnSpy = sandbox.spy(driver.log, 'warn');

      for (const zone of [
        'Europe/Kyiv',
        'Asia/Kolkata',
        'Asia/Ho_Chi_Minh',
        'America/Argentina/Buenos_Aires',
        'Asia/Kathmandu',
        'Asia/Yangon',
        'US/Eastern',
        'PST8PDT',
        'CET',
      ]) {
        shellStub.resetHistory();
        getTimeZoneStub.resetHistory();
        getTimeZoneStub.resolves(zone);
        await adjustTimeZone.call(driver, zone);
        sinon.assert.calledOnceWithExactly(shellStub, ['service', 'call', 'alarm', '3', 's16', zone]);
        sinon.assert.calledOnce(getTimeZoneStub);
      }
      assert.strictEqual(warnSpy.callCount, 0);
    });

    it('warns when the device reports a different time zone after setting it', async function () {
      const shellStub = sandbox.stub(driver.adb, 'shell').resolves('');
      const getTimeZoneStub = sandbox.stub(driver.adb, 'getTimeZone').resolves('GMT\n');
      const warnSpy = sandbox.spy(driver.log, 'warn');

      for (const zone of ['Europe/Kyiv', 'Us/Eastern']) {
        shellStub.resetHistory();
        getTimeZoneStub.resetHistory();
        warnSpy.resetHistory();

        await adjustTimeZone.call(driver, zone);

        sinon.assert.calledOnceWithExactly(shellStub, ['service', 'call', 'alarm', '3', 's16', zone]);
        sinon.assert.calledOnce(getTimeZoneStub);
        sinon.assert.calledOnce(warnSpy);
        assert.ok(String(warnSpy.firstCall.args[0]).includes(`'GMT'`));
        assert.ok(String(warnSpy.firstCall.args[0]).includes(`'${zone}'`));
      }
    });

    it('does not fail if reading back the device time zone fails', async function () {
      const shellStub = sandbox.stub(driver.adb, 'shell').resolves('');
      const getTimeZoneStub = sandbox.stub(driver.adb, 'getTimeZone').rejects(new Error('getprop failed'));
      const warnSpy = sandbox.spy(driver.log, 'warn');

      await adjustTimeZone.call(driver, 'UTC');

      sinon.assert.calledOnceWithExactly(shellStub, ['service', 'call', 'alarm', '3', 's16', 'UTC']);
      sinon.assert.calledOnce(getTimeZoneStub);
      assert.strictEqual(warnSpy.callCount, 0);
    });

    it('rejects invalid, offset, and malformed time zones', async function () {
      const shellStub = sandbox.stub(driver.adb, 'shell').resolves('');
      const getTimeZoneStub = sandbox.stub(driver.adb, 'getTimeZone');

      for (const invalidZone of [
        '',
        ' ',
        ' UTC',
        'UTC ',
        'UTC\n',
        'UTC\t',
        'UTC;id',
        '/America/New_York',
        'America/New_York/',
        'America//New_York',
        'Invalid/Zone',
        'Etc/GMT+15',
        'Factory',
        '+05:00',
        '-04:00',
        '+00:00',
        '+0530',
        '+05',
        'GMT+05:00',
        'UTC+05:00',
        'GMT+5',
        'UTC+5',
        'Z',
        'utc',
        'gmt',
        'etc/utc',
        'america/new_york',
        'europe/kyiv',
        'etc/gmt+5',
        undefined as unknown as string,
        null as unknown as string,
      ]) {
        await assert.rejects(
          adjustTimeZone.call(driver, invalidZone),
          (err: unknown) =>
            err instanceof Error &&
            err.message.includes(`The provided time zone identifier '${String(invalidZone)}' is not known`),
          `Expected '${String(invalidZone)}' to be rejected`,
        );
      }
      assert.strictEqual(shellStub.callCount, 0);
      assert.strictEqual(getTimeZoneStub.callCount, 0);
    });
  });
});
