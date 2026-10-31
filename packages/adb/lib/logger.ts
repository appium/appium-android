import {logger} from '@appium/support';
import type {AppiumLogger, AppiumLoggerLevel} from '@appium/types';

export const log = logger.getLogger('ADB');

export type ADBLogger = Pick<AppiumLogger, AppiumLoggerLevel | 'errorWithException' | 'errorAndThrow'>;

/** Filters one ADB instance's messages without changing the shared logger level. */
export function createLogger(
  getLevel: () => AppiumLoggerLevel | undefined,
  destination: AppiumLogger = log,
): ADBLogger {
  const result = {
    errorWithException: destination.errorWithException,
    errorAndThrow: destination.errorAndThrow,
  } as ADBLogger;
  for (const level of destination.levels) {
    result[level] = (...args: any[]) => {
      const threshold = getLevel();
      if (threshold === undefined || destination.levels.indexOf(level) >= destination.levels.indexOf(threshold)) {
        destination[level](...args);
      }
    };
  }
  return result;
}
