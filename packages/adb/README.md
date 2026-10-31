appium-adb
==========

[![NPM version](http://img.shields.io/npm/v/appium-adb.svg)](https://npmjs.org/package/appium-adb)
[![Downloads](http://img.shields.io/npm/dm/appium-adb.svg)](https://npmjs.org/package/appium-adb)

A wrapper over [Android Debugger Bridge](https://developer.android.com/tools/adb), implemented using ES6
and along with `async/await`. This package is mainly used by Appium to perform all adb operations on Android devices.

## Installing

```bash
npm install appium-adb
```

## Watch

```bash
npm run dev
```

## Test

### unit tests

```bash
npm run test
```

### functional tests

By default the functional tests use an avd named `NEXUS_S_18_X86`, with API Level
18. To change this, you can use the environment variables `PLATFORM_VERSION`,
`API_LEVEL`, and `ANDROID_AVD`. If `PLATFORM_VERSION` is set then it is not
necessary to set `API_LEVEL` as it will be inferred.

```bash
npm run test:e2e
```

## Usage:

example:

```js
import { ADB } from 'appium-adb';

const adb = await ADB.createADB({logLevel: 'info'});
console.log(await adb.getProcessIdsByName('com.android.phone'));
```

`logLevel` optionally filters log messages for this ADB instance, including its
Logcat diagnostics. It does not change the shared Appium logger level or Android
logcat filter specifications. The global Appium log level still applies, so this
option can suppress additional messages but cannot enable messages suppressed by
the global level. If omitted, no additional filtering is applied.

Each instance has its own `logLevel`; changing it does not affect other instances.
`adb.clone()` inherits the current level and accepts a `logLevel` override.
Process-wide cache cleanup messages continue to use the shared logger.
