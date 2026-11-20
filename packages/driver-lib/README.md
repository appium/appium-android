[![NPM version](http://img.shields.io/npm/v/appium-android-driver.svg)](https://npmjs.org/package/appium-android-driver)
[![Downloads](http://img.shields.io/npm/dm/appium-android-driver.svg)](https://npmjs.org/package/appium-android-driver)

# Appium Android Driver

Appium Android Driver is the collection of common primitives used by other Android drivers, like UIA2/Espresso. This driver cannot work as a separate entity.

## ADB server host

Set the string capability `appium:adbHost` to the host running the ADB server.
The deprecated `appium:remoteAdbHost` remains supported. If both are set, the
first nonempty value in `appium:adbHost`, then `appium:remoteAdbHost`, is used.

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

