# android-apidemos

A fork of Google's Android ApiDemos application, used for testing Appium.

## Requirements

- Java 17
- Node.js (npm)

## Building

```bash
npm install
npm run build:debug --workspace android-apidemos
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Release (unsigned): `npm run build:release --workspace android-apidemos` → `app/build/outputs/apk/release/app-release-unsigned.apk`

## Download

The `android-apidemos@X.Y.Z` entries on [GitHub Releases](https://github.com/appium/appium-android/releases) include `ApiDemos-debug.apk` and `ApiDemos-release.apk`:

```text
https://github.com/appium/appium-android/releases/download/android-apidemos%40X.Y.Z/ApiDemos-debug.apk
```

## Releases

Versioned by Lerna together with the other monorepo packages (Conventional Commits). The version bump syncs `package.json` and `app/build.gradle` (`versionName` / `versionCode`), and the release workflow attaches the APKs to the GitHub release. The package is private and not published to npm.
