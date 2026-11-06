import assert from 'node:assert/strict';
import {describe, it, beforeEach, afterEach, before} from 'node:test';

import {ADB} from 'appium-adb';
import sinon from 'sinon';

import {DEVTOOLS_SOCKET_PATTERN} from '../../../lib/commands/context/helpers.js';
import * as webviewHelpers from '../../../lib/commands/context/helpers.js';
import {AndroidDriver} from '../../../lib/driver.js';

const sandbox = sinon.createSandbox();

describe('Webview Helpers', function () {
  const adb = new ADB();
  let driver = new AndroidDriver();
  let stubbedShell: sinon.SinonStub;

  before(async function () {
    driver = new AndroidDriver();
    driver.adb = adb;
  });

  afterEach(function () {
    sandbox.restore();
  });

  describe('DEVTOOLS_SOCKET_PATTERN', function () {
    it('patting patterns with webview_devtools_remote_22138', function () {
      assert.strictEqual(DEVTOOLS_SOCKET_PATTERN.test('@webview_devtools_remote_22138'), true);
    });
    it('patting patterns with webview_devtools_remote_m6x_27719', function () {
      assert.strictEqual(DEVTOOLS_SOCKET_PATTERN.test('@webview_devtools_remote_m6x_27719'), true);
    });
    it('patting patterns with chrome_devtools_remote', function () {
      assert.strictEqual(DEVTOOLS_SOCKET_PATTERN.test('@chrome_devtools_remote'), true);
    });
  });

  describe('When the webviews are obtained', function () {
    describe('for an app that embeds Chromium', function () {
      let webViews: string[];

      beforeEach(async function () {
        stubbedShell = sandbox.stub(adb, 'shell').callsFake(function () {
          return Promise.resolve(
            'Num       RefCount Protocol Flags    Type St Inode Path\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2818 /dev/socket/ss_conn_daemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  9231 @mcdaemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01 245445 @webview_devtools_remote_123\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2826 /dev/socket/installd\n',
          );
        }) as sinon.SinonStub;
        const webviewsMapping = await webviewHelpers.getWebViewsMapping.bind(driver)({
          androidDeviceSocket: 'webview_devtools_remote_123',
          ensureWebviewsHavePages: false,
          enableWebviewDetailsCollection: false,
        });
        webViews = webviewHelpers.parseWebviewNames.bind(driver)(webviewsMapping, {
          ensureWebviewsHavePages: false,
        });
      });

      it('then the unix sockets are queried', function () {
        assert.strictEqual(stubbedShell.calledOnce, true);
        assert.deepStrictEqual(stubbedShell.getCall(0).args[0], ['cat', '/proc/net/unix']);
      });

      it('then the webview is returned', function () {
        assert.strictEqual(webViews.length, 1);
        assert.deepStrictEqual(webViews, ['WEBVIEW_123']);
      });
    });

    describe('for a Chromium webview', function () {
      let webViews: string[];

      beforeEach(async function () {
        stubbedShell = sandbox.stub(adb, 'shell').callsFake(function () {
          return Promise.resolve(
            'Num       RefCount Protocol Flags    Type St Inode Path\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2818 /dev/socket/ss_conn_daemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  9231 @mcdaemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01 245445 @chrome_devtools_remote\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2826 /dev/socket/installd\n',
          );
        });

        const webviewsMapping = await webviewHelpers.getWebViewsMapping.bind(driver)({
          androidDeviceSocket: 'chrome_devtools_remote',
          ensureWebviewsHavePages: false,
          enableWebviewDetailsCollection: false,
        });
        webViews = webviewHelpers.parseWebviewNames.bind(driver)(webviewsMapping, {
          ensureWebviewsHavePages: false,
        });
      });

      it('then the unix sockets are queried', function () {
        assert.strictEqual(stubbedShell.calledOnce, true);
        assert.deepStrictEqual(stubbedShell.getCall(0).args[0], ['cat', '/proc/net/unix']);
      });

      it('then the webview is returned', function () {
        assert.strictEqual(webViews.length, 1);
        assert.deepStrictEqual(webViews, ['CHROMIUM']);
      });
    });

    describe('and the webview process may be frozen', function () {
      const unixSockets =
        'Num       RefCount Protocol Flags    Type St Inode Path\n' +
        '0000000000000000: 00000002 00000000 00010000 0001 01 245445 @webview_devtools_remote_123\n';
      let adbExec: sinon.SinonStub;

      async function getWebviews(cao: () => Promise<string>): Promise<void> {
        sandbox
          .stub(adb, 'shell')
          .callsFake(async (args) => ((args as string[])[0] === 'dumpsys' ? cao() : unixSockets));
        sandbox.stub(adb, 'getProcessNameById').resolves('io.appium.android.apis');
        adbExec = sandbox.stub(adb, 'adbExec').rejects(new Error('forward failed'));
        await webviewHelpers.getWebViewsMapping.bind(driver)();
      }

      function frozenApps(...apps: string[]): () => Promise<string> {
        return async () =>
          `  freeze_binder_async_threshold=1024\n  Apps frozen: ${apps.length}\n${apps.map((app) => `    ${app}\n`).join('')}`;
      }

      it('then a frozen webview process is not asked for its pages', async function () {
        await getWebviews(
          frozenApps('65581: 1855 com.android.cellbroadcastreceiver', '802918: 123 io.appium.android.apis (sticky)'),
        );
        assert.strictEqual(adbExec.called, false);
      });

      it('then a running webview process is still asked for its pages', async function () {
        await getWebviews(frozenApps('65581: 1234 com.android.cellbroadcastreceiver'));
        assert.strictEqual(adbExec.firstCall.args[0][0], 'forward');
      });

      it('then the webview is asked for its pages if frozen processes cannot be read', async function () {
        await getWebviews(async () => {
          throw new Error('Unknown command');
        });
        assert.strictEqual(adbExec.firstCall.args[0][0], 'forward');
      });
    });

    describe('and no webviews exist', function () {
      let webViews: string[];

      beforeEach(async function () {
        stubbedShell = sandbox.stub(adb, 'shell').callsFake(function () {
          return Promise.resolve(
            'Num       RefCount Protocol Flags    Type St Inode Path\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2818 /dev/socket/ss_conn_daemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  9231 @mcdaemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2826 /dev/socket/installd\n',
          );
        });

        const webviewsMapping = await webviewHelpers.getWebViewsMapping.bind(driver)();
        webViews = webviewHelpers.parseWebviewNames.bind(driver)(webviewsMapping, {
          ensureWebviewsHavePages: false,
          enableWebviewDetailsCollection: false,
        });
      });

      it('then the unix sockets are queried', function () {
        assert.strictEqual(stubbedShell.calledOnce, true);
        assert.deepStrictEqual(stubbedShell.getCall(0).args[0], ['cat', '/proc/net/unix']);
      });

      it('then no webviews are returned', function () {
        assert.strictEqual(webViews.length, 0);
      });
    });

    describe('and crosswalk webviews exist', function () {
      let webViews: string[];

      beforeEach(function () {
        stubbedShell = sandbox.stub(adb, 'shell').callsFake(function () {
          return Promise.resolve(
            'Num       RefCount Protocol Flags    Type St Inode Path\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2818 /dev/socket/ss_conn_daemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  9231 @mcdaemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01 245445 @com.application.myapp_devtools_remote\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2826 /dev/socket/installd\n',
          );
        });
      });

      describe('and the device socket is not specified', function () {
        beforeEach(async function () {
          const webviewsMapping = await webviewHelpers.getWebViewsMapping.bind(driver)({
            ensureWebviewsHavePages: false,
            enableWebviewDetailsCollection: false,
          });
          webViews = webviewHelpers.parseWebviewNames.bind(driver)(webviewsMapping, {
            ensureWebviewsHavePages: false,
          });
        });

        it('then the unix sockets are queried', function () {
          assert.strictEqual(stubbedShell.calledOnce, true);
          assert.deepStrictEqual(stubbedShell.getCall(0).args[0], ['cat', '/proc/net/unix']);
        });

        it('then the webview is returned', function () {
          assert.strictEqual(webViews.length, 1);
          assert.deepStrictEqual(webViews, ['WEBVIEW_com.application.myapp']);
        });
      });

      describe('and the device socket is specified', function () {
        beforeEach(async function () {
          const webviewsMapping = await webviewHelpers.getWebViewsMapping.bind(driver)({
            androidDeviceSocket: 'com.application.myapp_devtools_remote',
            ensureWebviewsHavePages: false,
            enableWebviewDetailsCollection: false,
          });
          webViews = webviewHelpers.parseWebviewNames.bind(driver)(webviewsMapping, {
            ensureWebviewsHavePages: false,
          });
        });

        it('then the unix sockets are queried', function () {
          assert.strictEqual(stubbedShell.calledOnce, true);
          assert.deepStrictEqual(stubbedShell.getCall(0).args[0], ['cat', '/proc/net/unix']);
        });

        it('then the webview is returned', function () {
          assert.strictEqual(webViews.length, 1);
          assert.deepStrictEqual(webViews, ['WEBVIEW_com.application.myapp']);
        });
      });

      describe('and the device socket is specified but is not found', function () {
        beforeEach(async function () {
          const webviewsMapping = await webviewHelpers.getWebViewsMapping.bind(driver)({
            androidDeviceSocket: 'com.application.myotherapp_devtools_remote',
          });
          webViews = webviewHelpers.parseWebviewNames.bind(driver)(webviewsMapping);
        });

        it('then the unix sockets are queried', function () {
          assert.strictEqual(stubbedShell.calledOnce, true);
          assert.deepStrictEqual(stubbedShell.getCall(0).args[0], ['cat', '/proc/net/unix']);
        });

        it('then no webviews are returned', function () {
          assert.strictEqual(webViews.length, 0);
        });
      });
    });

    describe('and stetho socket exists', function () {
      let webViews: string[];

      beforeEach(function () {
        stubbedShell = sandbox.stub(adb, 'shell').callsFake(function () {
          return Promise.resolve(
            'Num       RefCount Protocol Flags    Type St Inode Path\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2818 /dev/socket/ss_conn_daemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  9231 @mcdaemon\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01 245445 @stetho_com.google.android.apps.messaging_devtools_remote\n' +
              '0000000000000000: 00000002 00000000 00010000 0001 01  2826 /dev/socket/installd\n',
          );
        });
      });

      describe('and the device socket is not specified', function () {
        beforeEach(async function () {
          const webviewsMapping = await webviewHelpers.getWebViewsMapping.bind(driver)({
            ensureWebviewsHavePages: false,
            enableWebviewDetailsCollection: false,
          });
          webViews = webviewHelpers.parseWebviewNames.bind(driver)(webviewsMapping, {
            ensureWebviewsHavePages: false,
          });
        });

        it('then the unix sockets are queried', function () {
          assert.strictEqual(stubbedShell.calledOnce, true);
          assert.deepStrictEqual(stubbedShell.getCall(0).args[0], ['cat', '/proc/net/unix']);
        });

        it('then the stetho socket is skipped and no webviews are returned', function () {
          assert.strictEqual(webViews.length, 0);
        });
      });
    });
  });
});
