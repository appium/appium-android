import type {EventEmitter} from 'node:events';

import type {WebDriverProxy} from '@appium/base-driver';
import type {AppiumLogger} from '@appium/types';
import type * as TeenProcess from 'teen_process';

import type {ChromedriverStorageClient} from '../storage-client/storage-client.js';
import type {ChromedriverAdb, ChromedriverOpts} from '../types.js';

export interface ChromedriverCommandContext extends EventEmitter {
  state: string;
  proxyPort: number;
  adb?: ChromedriverAdb;
  cmdArgs?: string[];
  logPath?: string;
  disableBuildCheck: boolean;
  mappingPath?: string;
  executableDir: string;
  isCustomExecutableDir: boolean;
  useSystemExecutable: boolean;
  chromedriver?: string;
  executableVerified: boolean;
  bundleId?: string;
  details?: ChromedriverOpts['details'];
  storageClient: ChromedriverStorageClient | null;
  _execFunc: typeof TeenProcess.exec;
  _driverVersion: string | null;
  _onlineStatus: Record<string, any> | null;
  capabilities: Record<string, any>;
  jwproxy: WebDriverProxy;
  log: AppiumLogger;
  driverVersion: string | null;
}
