import assert from 'node:assert/strict';
import path from 'node:path';
import {describe, it, beforeEach, afterEach, mock} from 'node:test';

import * as appiumSupport from '@appium/support';
import {zip} from '@appium/support';
import type {ZipEntry} from '@appium/support';
import sinon from 'sinon';
import * as teenProcess from 'teen_process';

import * as utilsIndex from '../../lib/utils/index.js';
import {FIXTURES_ROOT, MODULE_ROOT} from '../constants.js';

const keystorePath = path.resolve(FIXTURES_ROOT, 'appiumtest.keystore');
const keysRoot = path.resolve(MODULE_ROOT, 'keys');
const defaultKeyPath = path.resolve(keysRoot, 'testkey.pk8');
const defaultCertPath = path.resolve(keysRoot, 'testkey.x509.pem');
const keyAlias = 'appiumtest';
const password = 'android';
const javaDummyPath = 'java_dummy_path';
const javaHome = 'java_home';
const apksignerDummyPath = '/path/to/apksigner';
const fs = appiumSupport.fs;

let currentExec: (...args: any[]) => any = async () => ({stdout: '', stderr: ''});
let currentGetResourcePath: (...args: any[]) => any = async () => '';
let currentGetJavaForOs: (...args: any[]) => any = async () => javaDummyPath;
let currentGetJavaHome: (...args: any[]) => any = async () => javaHome;
let currentReadEntries: (...args: any[]) => any = async () => {};
let currentTempDirPath: (...args: any[]) => any = async () => '';

mock.module('teen_process', {
  namedExports: {
    ...teenProcess,
    exec: (...args: any[]) => currentExec(...args),
  },
});
// `default` is not a valid named export
const {default: _default, ...supportExports} = appiumSupport as Record<string, any>;
mock.module('@appium/support', {
  namedExports: {
    ...supportExports,
    zip: {...zip, readEntries: (...args: any[]) => currentReadEntries(...args)},
    tempDir: {...appiumSupport.tempDir, path: (...args: any[]) => currentTempDirPath(...args)},
  },
});
mock.module('../../lib/utils/index.js', {
  namedExports: {
    ...utilsIndex,
    getResourcePath: (...args: any[]) => currentGetResourcePath(...args),
    getJavaForOs: (...args: any[]) => currentGetJavaForOs(...args),
    getJavaHome: (...args: any[]) => currentGetJavaHome(...args),
  },
});

const {ADB} = await import('../../lib/adb.js');

const adb = new ADB();
adb.keystorePath = keystorePath;
adb.keyAlias = keyAlias;
adb.keystorePassword = password;
adb.keyPassword = password;

describe('signing', function () {
  let sandbox: sinon.SinonSandbox;
  let mocks: {
    adb: any;
    appiumSupport: any;
    fs: any;
  };
  const apiDemosPath = path.resolve(FIXTURES_ROOT, 'ApiDemos-debug.apk');

  beforeEach(function () {
    sandbox = sinon.createSandbox();
    mocks = {
      adb: sandbox.mock(adb),
      appiumSupport: sandbox.mock(appiumSupport),
      fs: sandbox.mock(fs),
    };
  });

  afterEach(function () {
    sandbox.verify();
    sandbox.restore();
  });

  describe('signWithDefaultCert', function () {
    it('should call exec with correct args', async function () {
      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      const getResourcePathStub = sandbox.stub();
      getResourcePathStub.withArgs(path.join('keys', 'testkey.pk8')).returns(defaultKeyPath);
      getResourcePathStub.withArgs(path.join('keys', 'testkey.x509.pem')).returns(defaultCertPath);
      currentGetResourcePath = getResourcePathStub;
      mocks.adb.expects('getBinaryFromSdkRoot').once().withExactArgs('apksigner.jar').returns(apksignerDummyPath);
      currentGetJavaForOs = sandbox.stub().returns(javaDummyPath);
      currentExec = sandbox
        .stub()
        .withArgs(javaDummyPath, sinon.match.array)
        .onFirstCall()
        .returns({stdout: '', stderr: ''});
      await adb.signWithDefaultCert(apiDemosPath);
    });

    it('should fail if apksigner fails', async function () {
      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      const getResourcePathStub = sandbox.stub();
      getResourcePathStub.withArgs(path.join('keys', 'testkey.pk8')).returns(defaultKeyPath);
      getResourcePathStub.withArgs(path.join('keys', 'testkey.x509.pem')).returns(defaultCertPath);
      currentGetResourcePath = getResourcePathStub;
      mocks.adb.expects('getBinaryFromSdkRoot').once().withExactArgs('apksigner.jar').returns(apksignerDummyPath);
      currentGetJavaForOs = sandbox.stub().returns(javaDummyPath);
      currentExec = sandbox
        .stub()
        .withArgs(javaDummyPath, sinon.match.array)
        .onFirstCall()
        .throws(new Error('apksigner failed'));
      await assert.rejects(adb.signWithDefaultCert(apiDemosPath));
    });

    it('should throw error for invalid file path', async function () {
      const dummyPath = 'dummyPath';
      await assert.rejects(adb.signWithDefaultCert(dummyPath));
    });
  });

  describe('signWithCustomCert', function () {
    let innerExecStub: sinon.SinonStub;
    it('should call exec with correct args', async function () {
      adb.useKeystore = true;

      mocks.fs.expects('exists').once().withExactArgs(keystorePath).returns(true);
      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      mocks.adb.expects('getBinaryFromSdkRoot').once().withExactArgs('apksigner.jar').returns(apksignerDummyPath);
      currentGetJavaForOs = sandbox.stub().returns(javaDummyPath);
      currentExec = sandbox
        .stub()
        .withArgs(javaDummyPath, sinon.match.array)
        .onFirstCall()
        .returns({stdout: '', stderr: ''});
      await adb.signWithCustomCert(apiDemosPath);
    });

    it('should fallback to jarsigner if apksigner fails', async function () {
      let jarsigner = path.resolve(javaHome, 'bin', 'jarsigner');
      if (appiumSupport.system.isWindows()) {
        jarsigner = jarsigner + '.exe';
      }
      adb.useKeystore = true;

      mocks.fs.expects('exists').once().withExactArgs(keystorePath).returns(true);
      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      mocks.adb.expects('getBinaryFromSdkRoot').once().withExactArgs('apksigner.jar').returns(apksignerDummyPath);
      currentGetJavaForOs = sandbox.stub().returns(javaDummyPath);
      currentGetJavaHome = sandbox.stub().returns(javaHome);
      innerExecStub = sandbox.stub();
      innerExecStub.withArgs(javaDummyPath).throws(new Error('apksigner failed'));
      innerExecStub
        .withArgs(jarsigner, [
          '-sigalg',
          'MD5withRSA',
          '-digestalg',
          'SHA1',
          '-keystore',
          keystorePath,
          '-storepass',
          password,
          '-keypass',
          password,
          apiDemosPath,
          keyAlias,
        ])
        .returns({});
      currentExec = innerExecStub;
      // Mock zip.readEntries to indicate no META-INF (so unsignApk returns false)
      /* eslint-disable promise/prefer-await-to-callbacks -- zip.readEntries is callback-based */
      currentReadEntries = async (_apkPath: string, callback: (arg: any) => void) => {
        // Call callback with a non-META-INF entry so hasMetaInf stays false
        callback({
          entry: {fileName: 'AndroidManifest.xml'},
          extractEntryTo: async () => {},
        } as ZipEntry);
      };
      /* eslint-enable promise/prefer-await-to-callbacks */
      await adb.signWithCustomCert(apiDemosPath);
      assert.strictEqual(innerExecStub.callCount, 2);
    });
  });

  describe('zipAlignApk', function () {
    const zipalign = 'zipalign_dummy_path';
    const alignedApk = path.join('tmp_dir', 'aligned.tmp');
    let origBinaries: typeof adb.binaries;
    let execCalls: string[][];

    beforeEach(function () {
      origBinaries = adb.binaries;
      adb.binaries = {...origBinaries, zipalign} as typeof adb.binaries;
      execCalls = [];
      mocks.adb.expects('initZipAlign').once().withExactArgs().resolves();
    });

    afterEach(function () {
      adb.binaries = origBinaries;
    });

    it('should do nothing if the apk is already zip-aligned', async function () {
      currentExec = async (_bin: string, args: string[]) => {
        execCalls.push(args);
        return {stdout: '', stderr: ''};
      };
      assert.strictEqual(await adb.zipAlignApk(apiDemosPath), false);
      assert.deepStrictEqual(execCalls, [['-c', '4', apiDemosPath]]);
    });

    it('should zip-align the apk and replace the original one', async function () {
      currentExec = async (_bin: string, args: string[]) => {
        execCalls.push(args);
        if (args[0] === '-c') {
          throw new Error('not aligned');
        }
        return {stdout: '', stderr: ''};
      };
      mocks.fs.expects('access').once().withArgs(apiDemosPath).resolves();
      currentTempDirPath = async (opts: unknown) => {
        assert.deepStrictEqual(opts, {prefix: 'appium', suffix: '.tmp'});
        return alignedApk;
      };
      mocks.fs.expects('mkdirp').once().withExactArgs(path.dirname(alignedApk)).resolves();
      mocks.fs.expects('mv').once().withExactArgs(alignedApk, apiDemosPath, {mkdirp: true}).resolves();
      assert.strictEqual(await adb.zipAlignApk(apiDemosPath), true);
      assert.deepStrictEqual(execCalls, [
        ['-c', '4', apiDemosPath],
        ['-f', '4', apiDemosPath, alignedApk],
      ]);
    });

    it('should throw if the apk is not writeable', async function () {
      currentExec = async () => {
        throw new Error('not aligned');
      };
      mocks.fs.expects('access').once().withArgs(apiDemosPath).rejects(new Error('EACCES'));
      await assert.rejects(adb.zipAlignApk(apiDemosPath), /is not writeable/);
    });
  });

  describe('checkApkCert', function () {
    it('should return false for apk not present', async function () {
      mocks.fs.expects('exists').once().withExactArgs('dummyPath').returns(false);
      assert.strictEqual(await adb.checkApkCert('dummyPath'), false);
    });

    it('should check default signature when not using keystore', async function () {
      adb.useKeystore = false;

      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      mocks.fs.expects('hash').once().withExactArgs(apiDemosPath).returns(Math.random().toString(36));
      mocks.adb.expects('getBinaryFromSdkRoot').twice().withExactArgs('apksigner.jar').returns(apksignerDummyPath);
      currentGetJavaForOs = sandbox.stub().returns(javaDummyPath);
      currentExec = sandbox
        .stub()
        .withArgs(javaDummyPath, sinon.match.array)
        .onFirstCall()
        .returns({
          stdout: `
      Signer #1 certificate DN: EMAILADDRESS=android@android.com, CN=Android, OU=Android, O=Android, L=Mountain View, ST=California, C=US
      Signer #1 certificate SHA-256 digest: a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc
      Signer #1 certificate SHA-1 digest: 61ed377e85d386a8dfee6b864bd85b0bfaa5af81
      Signer #1 certificate MD5 digest: e89b158e4bcf988ebd09eb83f5378e87`,
          stderr: '',
        });
      assert.strictEqual(await adb.checkApkCert(apiDemosPath), true);
    });

    it('should check non default signature when not using keystore', async function () {
      adb.useKeystore = false;

      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      mocks.fs.expects('hash').once().withExactArgs(apiDemosPath).returns(Math.random().toString(36));
      mocks.adb.expects('getBinaryFromSdkRoot').twice().withExactArgs('apksigner.jar').returns(apksignerDummyPath);
      currentGetJavaForOs = sandbox.stub().returns(javaDummyPath);
      currentExec = sandbox
        .stub()
        .withArgs(javaDummyPath, sinon.match.array)
        .onFirstCall()
        .returns({
          stdout: `
      Signer #1 certificate DN: EMAILADDRESS=android@android.com, CN=Android, OU=Android, O=Android, L=Mountain View, ST=California, C=US
      Signer #1 certificate SHA-256 digest: a40da80a59d170caa950cf15cccccc4d47a39b26989d8b640ecd745ba71bf5dc
      Signer #1 certificate SHA-1 digest: 61ed377e85d386a8dfee6b864bdcccccfaa5af81
      Signer #1 certificate MD5 digest: e89b158e4bcf988ebd09eb83f53ccccc`,
          stderr: '',
        });
      const result = await adb.checkApkCert(apiDemosPath, {
        requireDefaultCert: false,
      });
      assert.strictEqual(result, true);
    });

    it('should fail if apksigner is not found', async function () {
      adb.useKeystore = false;

      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      mocks.fs.expects('hash').once().withExactArgs(apiDemosPath).returns(Math.random().toString(36));
      mocks.adb
        .expects('getBinaryFromSdkRoot')
        .once()
        .withExactArgs('apksigner.jar')
        .throws(new Error('apksigner not found'));
      await assert.rejects(adb.checkApkCert(apiDemosPath));
    });

    it('should call getKeystoreHash when using keystore', async function () {
      adb.useKeystore = true;

      mocks.fs.expects('exists').once().withExactArgs(apiDemosPath).returns(true);
      mocks.fs.expects('hash').once().withExactArgs(apiDemosPath).returns(Math.random().toString(36));
      mocks.adb.expects('getKeystoreHash').once().returns({
        md5: 'e89b158e4bcf988ebd09eb83f53ccccc',
        sha1: '61ed377e85d386a8dfee6b864bdcccccfaa5af81',
        sha256: 'a40da80a59d170caa950cf15cccccc4d47a39b26989d8b640ecd745ba71bf5dc',
      });
      mocks.adb.expects('getBinaryFromSdkRoot').twice().withExactArgs('apksigner.jar').returns(apksignerDummyPath);
      currentGetJavaForOs = sandbox.stub().returns(javaDummyPath);
      currentExec = sandbox
        .stub()
        .withArgs(javaDummyPath, sinon.match.array)
        .onFirstCall()
        .returns({
          stdout: `
      Signer #1 certificate DN: EMAILADDRESS=android@android.com, CN=Android, OU=Android, O=Android, L=Mountain View, ST=California, C=US
      Signer #1 certificate SHA-256 digest: a40da80a59d170caa950cf15cccccc4d47a39b26989d8b640ecd745ba71bf5dc
      Signer #1 certificate SHA-1 digest: 61ed377e85d386a8dfee6b864bdcccccfaa5af81
      Signer #1 certificate MD5 digest: e89b158e4bcf988ebd09eb83f53ccccc`,
          stderr: '',
        });
      assert.strictEqual(await adb.checkApkCert(apiDemosPath), true);
    });
  });
});
