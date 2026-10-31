import type {AppiumLoggerLevel} from '@appium/types';

import type {Logcat} from './logcat.js';
import type {StringRecord} from './tools/types.js';

export interface ADBOptions {
  sdkRoot?: string;
  udid?: string;
  useKeystore?: boolean;
  keystorePath?: string;
  keystorePassword?: string;
  keyAlias?: string;
  keyPassword?: string;
  executable?: ADBExecutable;
  tmpDir?: string;
  curDeviceId?: string;
  emulatorPort?: number;
  logcat?: Logcat;
  binaries?: StringRecord;
  suppressKillServer?: boolean;
  adbPort?: number;
  adbHost?: string;
  adbExecTimeout?: number;
  remoteAppsCacheLimit?: number;
  buildToolsVersion?: string;
  allowOfflineDevices?: boolean;
  allowDelayAdb?: boolean;
  remoteAdbHost?: string;
  remoteAdbPort?: number;
  clearDeviceLogsOnStart?: boolean;
  listenAllNetwork?: boolean;
  /** Additional per-instance log threshold. The global Appium log level still applies. */
  logLevel?: AppiumLoggerLevel;
}

export interface ADBExecutable {
  path: string;
  defaultArgs: string[];
}
