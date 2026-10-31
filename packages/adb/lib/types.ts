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
  /**
   * Sets the shared Appium logger level when an ADB instance is created.
   * This is not an instance-specific or ADB-only setting: other ADB instances
   * and packages using the same underlying logger in the same process share
   * the updated level. The last explicitly supplied level wins.
   *
   * Omitting this option leaves the shared level unchanged; it does not restore
   * a default. `adb.clone()` inherits this option and reapplies its level unless
   * overridden. Assigning to `adb.logLevel` after construction does not update
   * the shared logger level.
   *
   * In standalone usage, this controls the shared logger's direct output.
   * Inside the Appium server, log events are forwarded to Winston, which controls
   * server log output separately. This option does not change the server's
   * `--log-level` setting and should not be used to filter ADB server log messages.
   *
   * This option does not configure Android logcat priorities or filter specifications.
   */
  logLevel?: AppiumLoggerLevel;
}

export interface ADBExecutable {
  path: string;
  defaultArgs: string[];
}
