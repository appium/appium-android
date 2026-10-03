# compose-playground

A small Android fixture app for exploring and validating **Jetpack Compose** UI behavior alongside classic Android views. The home screen uses traditional `TextView` menu items; each demo screen is built with Compose.

Use it for manual exploration, instrumented Compose UI tests, or as a stable APK in your own automation pipelines.

## Download

Install the debug APK from the `compose-playground@X.Y.Z` entries on [GitHub Releases](https://github.com/appium/appium-android/releases).

Versioned URL pattern:

```text
https://github.com/appium/appium-android/releases/download/compose-playground%40X.Y.Z/ComposePlayground-debug.apk
```

## Requirements

- JDK 17
- Android SDK Platform 37 (compile SDK)
- Node.js 20+ and npm 10+ (monorepo tooling)

## Build locally

```bash
npm install
npm run build:debug --workspace compose-playground
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Release (unsigned): `npm run build:release --workspace compose-playground` → `app/build/outputs/apk/release/app-release-unsigned.apk`

### Instrumented tests

With an API 26+ emulator or device connected:

```bash
npm run test:e2e --workspace compose-playground
```

## Demo catalog

| Menu label (classic UI) | Compose semantics | What to try |
|-------------------------|-------------------|-------------|
| Clickable Component | `testTag("lol")`, `contentDescription = "desc"`, text **Click to see dialog**; dialog **Congratulations! You just clicked the text successfully** | Tap by tag, content description, or text; confirm dialog |
| Horizontal Carousel | Two nodes with text **Grace Hopper** | Find duplicate visible text in a horizontal list |
| Display Text | Non-empty Compose tree | Inspect hierarchy / page source |
| Text Input Components | `testTag("text_input")`, initial text **Enter your text here** | Append, replace, and clear field text |

Each Compose screen sets `testTagsAsResourceId = true` on the root so `testTag` values are exposed as view resource IDs ([Compose testing interoperability](https://developer.android.com/develop/ui/compose/testing/interoperability)).

## CI and releases

- **Compose Playground CI** — builds debug and release APKs and runs `connectedDebugAndroidTest` on an emulator for pull requests touching this package.
- **Releases** — versioned by Lerna together with the other monorepo packages (Conventional Commits). The version bump syncs `package.json` and `app/build.gradle.kts` (`versionName` / `versionCode`), and the release workflow attaches `ComposePlayground-debug.apk` and `ComposePlayground-release.apk` to the `compose-playground@X.Y.Z` GitHub release. The package is private and not published to npm.

## Compatibility

- **minSdk** 26
- **targetSdk** 34, **compileSdk** 37
- Compose BOM is pinned in `gradle/libs.versions.toml`; bump it when you need newer Compose APIs.

## License

Apache-2.0 — see [LICENSE](LICENSE).
