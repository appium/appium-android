# @appium/xpath2-processor

The [PsychoPath XPath 2.0 processor](https://eclipse.googlesource.com/sourceediting/webtools.sourceediting.git) from Eclipse WTP, vendored once and shared by the Appium Android servers (`appium-uiautomator2-server`, and the Espresso server).

This is a private monorepo package: it is not published to npm. It is versioned by Lerna with the other packages, and the Gradle artifacts are served by [JitPack](https://jitpack.io).

## Coordinates

```kotlin
repositories {
    maven {
        url = uri("https://jitpack.io")
        content { includeGroup("com.github.appium.appium-android") }
    }
}

dependencies {
    implementation("com.github.appium.appium-android:xpath2-processor:<version>")
    // optional: pins xerces and java-cup-runtime
    implementation(platform("com.github.appium.appium-android:xpath2-bom:<version>"))
}
```

The build publishes two modules (`xpath2-processor` and `xpath2-bom`); JitPack only uses the `com.github.appium.appium-android:<artifact>` form when a build publishes more than one. The runtime dependencies (xerces, icu4j, java-cup-runtime) come transitively from the POM.

## Releases

1. Lerna bumps `package.json` and, through the `version` script, `xpath2Version` in `gradle.properties`.
2. The release workflow creates a plain `vX.Y.Z` tag for that version (JitPack ignores Lerna's `name@X.Y.Z` tags) and requests the artifact from JitPack so it is built before anyone depends on it.

`jitpack.yml` at the repository root builds the package with `./gradlew clean publishToMavenLocal -x test` on JDK 17. Run `npm run package:local --workspace @appium/xpath2-processor` to reproduce it locally (installs into `~/.m2`).

## Developing against a local copy

`appium-uiautomator2-server` builds this package from source (Gradle composite build, see its `settings.gradle.kts`).

## Vendored `org.eclipse.wst.xml.xpath2.processor`

Upstream: [Eclipse WTP Source Editing](https://eclipse.googlesource.com/sourceediting/webtools.sourceediting.git) (`xpath/bundles/org.eclipse.wst.xml.xpath2.processor/`).

The pinned upstream commit is recorded in [`SOURCE_COMMIT`](./SOURCE_COMMIT).

Local change to re-apply when re-vendoring: ICU4J (a 14 MB dependency) is replaced by the JDK, i.e. `Character` / `String` code point methods and `java.text.Normalizer` for `fn:normalize-unicode`.

### License and notice

This component is **PsychoPath XPath 2.0 Processor** from the Eclipse Web Tools Platform.

- **License:** [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/) (EPL-2.0). Source files retain their original copyright headers (`SPDX-License-Identifier: EPL-2.0`).
- **About / redistribution notice:** [`org.eclipse.wst.xml.xpath2.processor/about.html`](./org.eclipse.wst.xml.xpath2.processor/about.html) (upstream “About This Content” text, unchanged).
- **Bundle metadata:** [`plugin.properties`](./org.eclipse.wst.xml.xpath2.processor/plugin.properties), [`META-INF/MANIFEST.MF`](./org.eclipse.wst.xml.xpath2.processor/META-INF/MANIFEST.MF).

`about.html`, `plugin.properties`, and `META-INF/` are included in the built JAR, matching the upstream Eclipse bundle layout.

### Build the JAR

```bash
npm run build --workspace @appium/xpath2-processor   # ./gradlew jar
```

The output is written to `build/libs/`.

## Upstream unit tests

Test **sources** (with local harness patches) live under [`org.eclipse.wst.xml.xpath2.processor.tests/src`](./org.eclipse.wst.xml.xpath2.processor.tests/src/).

Large test **fixtures** are not stored in git. They are sparse-cloned from the same upstream repository at `SOURCE_COMMIT` into `.upstream/webtools.sourceediting/` when you run tests:

- `xpath/tests/org.w3c.xqts.testsuite` — W3C XPath 2.0 vectors (`TestSources/`, `Queries/`, `ExpectedTestResults/`)
- `xpath/tests/org.eclipse.wst.xml.xpath2.processor.tests` — bug regression XML/XSD fixtures (`bugTestFiles/`, etc.)

Eclipse/OSGi resource loading is replaced with classpath lookup via `TestResourceBundles`.

### Run tests

```bash
npm run test:java --workspace @appium/xpath2-processor   # ./gradlew test
```

The `fetchXpath2TestData` task runs automatically before `test` (requires network on first run). CI runs this in `xpath2-processor-ci.yml`.

This executes the upstream `AllPsychoPathTests` aggregate suite (8000+ cases) through `PsychoPathTestSuiteAdapter`.
