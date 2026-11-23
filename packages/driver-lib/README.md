[![NPM version](http://img.shields.io/npm/v/appium-android-driver.svg)](https://npmjs.org/package/appium-android-driver)
[![Downloads](http://img.shields.io/npm/dm/appium-android-driver.svg)](https://npmjs.org/package/appium-android-driver)

# Appium Android Driver

Appium Android Driver is the collection of common primitives used by other Android drivers, like UIA2/Espresso. This driver cannot work as a separate entity.

## ADB server host

Set the string capability `appium:adbHost` to the host running the ADB server and
`appium:adbPort` to its port. The deprecated `appium:remoteAdbHost` remains
supported. The first nonempty host is used, with `appium:adbHost` preferred over
`appium:remoteAdbHost` and session capabilities preferred over driver defaults.

Android driver subclasses can use `this.getAdbHost()` to resolve the same host
for their downstream connections, including when they call `BaseDriver.createSession`
directly. The resolver leaves the original session capabilities and options intact.

For remote ADB sessions, keep using `appium:remoteAdbHost` with UiAutomator2 and
Espresso until the installed driver uses `getAdbHost()` for its proxy connection.
With older versions, setting only `appium:adbHost` can send ADB commands to the
remote server while the proxy still connects to the local machine.

## Examples

- [`examples/media-stream-client.mts`](examples/media-stream-client.mts): a Node.js client that decodes the `appium:mediaStream` BiDi events into a local MJPEG HTTP server or an `.mp4` file written by `ffmpeg`. See `mobile: startMediaStream` in the driver docs.

## Development

### Install and Compile

```
npm i
npm run build
npm run lint
```

### Unit Test

```
npm test
```

