appium-chromedriver
===================

Node.js wrapper around [Chromedriver](https://sites.google.com/chromium.org/driver/)
and [Microsoft Edge WebDriver](https://developer.microsoft.com/en-us/microsoft-edge/tools/webdriver/).
The Microsoft Edge WebDriver support is since v5.4.0.
This wrapper is not used directly in Appium, but rather by various Android drivers to automate
Chrome/Chromium-based browsers
and web views using Hybrid Mode approach. Check the corresponding driver tutorials to get
more details on it.

> **Note**
>
> This package is intended to be used as a helper module for Appium drivers such as
> [UiAutomator2](https://github.com/appium/appium-uiautomator2-driver/) and
> [appium-chromium-driver](https://github.com/appium/appium-chromium-driver).
> It was not created for standalone usage.
> Please ensure you know what you are doing before using this package directly.

> **Note**
>
> This package can work with Microsoft Edge WebDriver as well, but the support is limited.
> For example, automatic downloads do not work for Microsoft Edge WebDriver.

## Executable verification

Before a Chromedriver executable is run, its SHA-256 checksum is compared to the allowlists in
[checksums.json](https://github.com/appium/appium-android/blob/main/packages/chromedriver/config/checksums.json)
(Chromedriver) and
[edge-checksums.json](https://github.com/appium/appium-android/blob/main/packages/chromedriver/config/edge-checksums.json)
(Microsoft Edge WebDriver). The first one is updated together with `mapping.json` by the Chromedriver version bump
workflow, the second one is updated by the Edge WebDriver checksums bump workflow. The Edge WebDriver allowlist
contains the latest stable and extended stable releases and the latest release of every major version
(Edge WebDriver builds older than major version 118 are not available for download anymore).
The automatic download feature is an explicit opt-in, so the executables it downloads are trusted locally
(their checksums are stored, one file per checksum, in the per-user writable
[Strongbox](https://github.com/appium/appium/tree/master/packages/strongbox) folder of the `appium-chromedriver` package).
The trust store is never read from `executableDir`, since that folder may be provided by the client.
Any other executable, including custom or self-built builds, drivers that are not on the allowlists
and the ones set by `executable`/`executableDir`, is rejected.

Driver integrations can call `installDriverFromArchive({archive, executableName, destination})`
after obtaining a ZIP stream from a distribution source they trust. The caller controls which source
can supply trusted drivers. The helper selects the uniquely named regular file in private staging,
registers its SHA-256, and publishes the executable at the destination. A registration failure preserves
the existing driver. If publication fails, the staged digest remains trusted for future launches.
Pass `trust: false` to install a driver from a source that is not trusted: its checksum is not registered, and
the executable must already be known (allowlisted or trusted). Otherwise the install fails **before anything is
published**, so a working driver already at the destination is preserved and an unknown binary never lands in a
shared cache. Use `trust: true` only for sources whose content you are allowed to trust.

`installDriverFromCdn({browser, version, destination, cdnUrl, platform, trust})` covers the common case
of both Chrome and Microsoft Edge: it downloads a driver from
[Chrome for Testing](https://googlechromelabs.github.io/chrome-for-testing/) (`browser: 'chrome'`) or from the
Microsoft Edge WebDriver storage (`browser: 'edge'`) and installs it as above.

- `version` is the exact **driver** version as published by the distribution, not the browser version. A browser
  patch version may have no driver of its own (and Chrome for Testing starts at 115), which results in a 404, so
  resolve a compatible driver release first (see
  [Chrome version selection](https://developer.chrome.com/docs/chromedriver/downloads/version-selection) and
  [Edge version matching](https://learn.microsoft.com/en-us/microsoft-edge/webdriver/)).
- `cdnUrl` (http(s) only) points to a mirror with the same layout. `platform` (e.g. `win64`, `mac-arm64`,
  `linux-arm64`) defaults to the current host. Chrome on Windows ARM64 uses the x64 driver, and there is no
  Edge driver for Linux ARM64.
- The download is limited to 200 MB and fails if it stalls.
- The checksum is registered as trusted by default only for the default distributions (`trust` defaults to `false`
  if `cdnUrl` is set).

When reusing the destination as a cache, install with the same `trust` that applies to the source of the build, and
check the destination before reusing it: a file installed with `trust: true` from a mirror is trusted afterwards
regardless of where the next session downloads from.

To restore the legacy behavior and run any executable without verification, set the
`allowUnverifiedExecutable` option. In the Appium UiAutomator2/Espresso drivers it is controlled by the
`chromedriver_allow_any_executable` server feature (`--allow-insecure=<driver>:chromedriver_allow_any_executable`).
Only enable it if you trust everyone who is able to create sessions.

The allowlists can be extended for older versions using
`node scripts/update_versions_mapping.mjs <latest version> config/mapping.json --backfill <N>` (Chromedriver) and
`node scripts/update_edge_checksums.mjs config/edge-checksums.json --backfill` (Microsoft Edge WebDriver).

## Automatic Chromedriver download on module install

Since version 6.0.0 of this module automatic download of the latest known chromedriver
does not happen anymore. The below information is only relevant for older module versions:

### Skipping binary installation

By default, upon installation the package downloads the most recent known Chromedriver version from
Chromedriver CDN server: http://chromedriver.storage.googleapis.com.
If, for some reason, you want to install the package without downloading the Chromedriver
binary set the `APPIUM_SKIP_CHROMEDRIVER_INSTALL` environment variable:

```bash
APPIUM_SKIP_CHROMEDRIVER_INSTALL=1 npm install appium-chromedriver
```

### Custom Chromedriver version

By default, the package uses the most recent known Chromedriver version.
The full list of known Chromedriver versions and their corresponding supported
Chrome version could be found in
[mapping.json](https://github.com/appium/appium-android/blob/main/packages/chromedriver/config/mapping.json)

To download a custom version of Chromedriver, please set `CHROMEDRIVER_VERSION` environment variable:

```bash
CHROMEDRIVER_VERSION=107.0.5304.62 npm install appium-chromedriver
```

## Custom binaries url

If you want Chromedriver to be downloaded from another CDN, which differs from the
default one https://chromedriver.storage.googleapis.com, then set the new URL to
the `CHROMEDRIVER_CDNURL` environment variable:

```bash
CHROMEDRIVER_CDNURL=http://npm.taobao.org/mirrors/chromedriver npm install appium-chromedriver
```

If you want automatic chromedrivers download feature to work with a custom CDN URL then make sure
the server returns a proper list of stored drivers in response to requests having
`Accept: application/xml` header. An example XML could be retrieved from the original URL using
`curl -H 'Accept: application/xml' https://chromedriver.storage.googleapis.com` command.

Since version 5.6 the second environment variable has been added: `CHROMELABS_URL`. By default, it points
to https://googlechromelabs.github.io, and is expected to contain the actual prefix of
[Chrome for Testing availability](https://github.com/GoogleChromeLabs/chrome-for-testing#json-api-endpoints)
JSON API. This API allows retrieval of chromedrivers whose major versions are greater than `114`.

Similarly to the above it could be also defined in the .npmrc file:

```bash
chromelabs_url=https://googlechromelabs.github.io
```

You may also want to skip checking for older Chromedriver versions by providing an
empty value to the `CHROMEDRIVER_CDNURL` variable.

## Usage

```js
import Chromedriver from 'appium-chromedriver';

// 'sync'-like await/Promise usage
async function runSession() {
    let driver = new Chromedriver();
    const desiredCaps = {browserName: 'chrome'};
    await driver.start(desiredCaps);
    let status = await driver.sendCommand('/status', 'GET');
    await driver.stop();
}

// EventEmitter usage
function runSession2() {
    let driver = new Chromedriver();
    const desiredCaps = {browserName: 'chrome'};
    driver.start(desiredCaps);
    driver.on(Chromedriver.EVENT_CHANGED, function (msg) {
        if (msg.state === Chromedriver.STATE_ONLINE) {
            driver.sendCommand('/status', 'GET').then(function (status) {
                driver.stop();
            });
        }
    });
    driver.on(Chromedriver.EVENT_ERROR, function (err) {
        // :-(
    });
}
```

## States

Here's what the Chromedriver state machine looks like:

![Chromedriver States](./doc/states.png)

Here are the events you can listen for:

* `Chromedriver.EVENT_ERROR`: gives you an error object
* `Chromedriver.EVENT_CHANGED`: gives you a state change object, with a `state` property that can be one of:
    * `Chromedriver.STATE_STOPPED`
    * `Chromedriver.STATE_STARTING`
    * `Chromedriver.STATE_ONLINE`
    * `Chromedriver.STATE_STOPPING`
    * `Chromedriver.STATE_RESTARTING`


## Development

### Build & Lint

```bash
npm run build
npm run lint
```

### Run Tests

```bash
npm run test
npm run test:e2e
```
