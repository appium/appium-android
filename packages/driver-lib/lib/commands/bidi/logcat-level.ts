import type {LogEntry} from 'appium-adb';

import {toLogRecord} from '../../utils.js';

const DEFAULT_FORMAT = 'threadtime';
// Same list as in appium-adb; an unknown format falls back to the default there
const KNOWN_FORMATS = ['brief', 'process', 'tag', 'thread', 'raw', 'time', 'threadtime', 'long'];
const TIME = String.raw`\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}`;
// Capture group 1 is the priority letter. `raw` and `long` carry no per-line priority.
const PRIORITY_PATTERNS: Readonly<Record<string, RegExp>> = {
  brief: /^([VDIWEF])\/.*?\(\s*\d+\):/,
  process: /^([VDIWEF])\(\s*\d+\)/,
  tag: /^([VDIWEF])\/[^:]*:/,
  thread: /^([VDIWEF])\(\s*\d+:\s*\d+\)/,
  time: new RegExp(String.raw`^${TIME}\s+([VDIWEF])/`),
  threadtime: new RegExp(String.raw`^${TIME}\s+\d+\s+\d+\s+([VDIWEF])\s`),
};
const LEVELS: Readonly<Record<string, string>> = {
  V: 'DEBUG',
  D: 'DEBUG',
  I: 'INFO',
  W: 'WARN',
  E: 'ERROR',
  F: 'ERROR',
};

/**
 * Builds a transformer that sets the entry level from the logcat priority of the line.
 * Lines without a recognizable priority are returned unchanged.
 *
 * @param format - The `logcat -v` format the capture was started with
 */
export function makeLogcatLevelTransformer(format?: string): (entry: LogEntry) => LogEntry {
  const pattern = PRIORITY_PATTERNS[format && KNOWN_FORMATS.includes(format) ? format : DEFAULT_FORMAT];
  return (entry) => {
    const priority = pattern?.exec(entry.message)?.[1];
    return priority ? toLogRecord(entry.timestamp, entry.message, LEVELS[priority]) : entry;
  };
}
