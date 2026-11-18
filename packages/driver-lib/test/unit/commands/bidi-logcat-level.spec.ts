import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import type {LogEntry} from 'appium-adb';

import {makeLogcatLevelTransformer} from '../../../lib/commands/bidi/logcat-level.js';

const entry = (message: string): LogEntry => ({timestamp: 1, level: 'ALL', message});

describe('makeLogcatLevelTransformer', function () {
  const cases: [string | undefined, string, string][] = [
    ['threadtime', '11-17 10:00:00.123  1234  5678 W ActivityManager: slow', 'WARN'],
    [undefined, '11-17 10:00:00.123  1234  5678 E tag: boom', 'ERROR'],
    ['no-such-format', '11-17 10:00:00.123  1234  5678 I tag: hi', 'INFO'],
    ['brief', 'D/tag( 1234): hello', 'DEBUG'],
    ['brief', 'E/Foo(Bar)( 1234): msg', 'ERROR'],
    ['brief', `E/${'t'.repeat(126)}`, 'ERROR'],
    ['tag', `W/${'t'.repeat(126)}`, 'WARN'],
    ['process', 'V( 1234) hello (tag)', 'DEBUG'],
    ['tag', 'F/tag: fatal', 'ERROR'],
    ['thread', 'I( 1234: 5678) hello (tag)', 'INFO'],
    ['time', '11-17 10:00:00.123 W/tag( 1234): hello', 'WARN'],
  ];
  for (const [format, line, level] of cases) {
    it(`should map '${line}' to ${level} for format ${String(format)}`, function () {
      assert.strictEqual(makeLogcatLevelTransformer(format)(entry(line)).level, level);
    });
  }

  it('should keep the message and timestamp', function () {
    const result = makeLogcatLevelTransformer('tag')({timestamp: 42, level: 'ALL', message: 'E/tag: x'});
    assert.deepStrictEqual(result, {timestamp: 42, level: 'ERROR', message: 'E/tag: x'});
  });

  it('should leave lines without a recognizable priority unchanged', function () {
    const unparsed = entry('--------- beginning of main');
    assert.strictEqual(makeLogcatLevelTransformer('threadtime')(unparsed), unparsed);
    // a threadtime line must not be parsed with the brief pattern
    const threadtimeLine = entry('11-17 10:00:00.123  1234  5678 E tag: boom');
    assert.strictEqual(makeLogcatLevelTransformer('brief')(threadtimeLine), threadtimeLine);
  });

  it('should leave all lines unchanged for formats without a per-line priority', function () {
    for (const format of ['raw', 'long']) {
      const line = entry('E/tag: not a priority in this format');
      assert.strictEqual(makeLogcatLevelTransformer(format)(line), line);
    }
  });
});
