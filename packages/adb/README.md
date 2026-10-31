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

### Logging

The optional `logLevel` option sets the level of the shared Appium logger when an
ADB instance is created. It is **not an instance-specific or ADB-only setting**:
other ADB instances and other packages using the same underlying logger in the
same process share the updated level. The last explicitly supplied level wins.
Omitting `logLevel` leaves the shared level unchanged; it does not restore a
default. `adb.clone()` inherits the option, so cloning an instance with a
`logLevel` also reapplies that level unless overridden.

When using `appium-adb` standalone, this setting controls the shared logger's
direct output. When running inside the Appium server, log events are forwarded
to Winston, which controls server log output separately. Setting this option
does not change the server's `--log-level` setting and should not be used to
filter ADB messages in the server logs.

This option does not configure Android logcat priorities or filter
specifications.
