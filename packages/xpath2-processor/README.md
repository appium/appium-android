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
}
```

The build publishes two modules (`xpath2-processor` and an empty `xpath2-bom`); JitPack only uses the `com.github.appium.appium-android:<artifact>` form when a build publishes more than one.

## Bundled dependencies

The published jar is self-contained: Xerces and java-cup-runtime are bundled with the processor and relocated into the `io.appium.xpath2.shaded` package (Gradle `shadowJar`), so the POM has no dependencies. This keeps them from clashing with copies of the same libraries in the process the processor is loaded into (the Espresso server shares a process and a classloader with the application under test).

- The parts of the DOM API that Android lacks (`org.w3c.dom.events`, `traversal`, `ranges`, `html`, `css`, `stylesheets`, `views`, `xpath`) are relocated as well. `org.w3c.dom`, `org.xml.sax` and `javax.xml` come from the platform and are not bundled.
- `META-INF/services` is dropped, so the JAXP factories of the host process are not affected.
- Because Xerces is relocated, do not import `org.apache.xerces` in the code that uses the processor. Use `io.appium.xpath2.DocumentLoader.parse(InputStream)` to parse the XML that expressions are evaluated against.
- `DocumentLoader` rejects DOCTYPE declarations. The upstream loaders (`DOMBuilder`, `XercesLoader`) are left as they are, since the conformance tests need DTDs, and are not reachable from an expression with the default `DynamicContextBuilder`.
- `./gradlew shadedTest` runs smoke tests against the shaded jar only.
- The jar carries the `META-INF/LICENSE` and `NOTICE` of Apache Xerces (Apache-2.0), which is bundled along with java-cup-runtime (CUP license) and the DOM interfaces of `xml-apis`.

## Releases

1. Lerna bumps `package.json` and, through the `version` script, `xpath2Version` in `gradle.properties`.
2. The release workflow creates a plain `vX.Y.Z` tag for that version (JitPack ignores Lerna's `name@X.Y.Z` tags) and requests the artifact from JitPack so it is built before anyone depends on it.

`jitpack.yml` at the repository root builds the package with `./gradlew clean publishToMavenLocal -x test` on JDK 17. Run `npm run package:local --workspace @appium/xpath2-processor` to reproduce it locally (installs into `~/.m2`).

## Developing against a local copy

`appium-uiautomator2-server` uses the released JitPack artifact. To build against the checked-out sources instead, pass `-PxpathLocal` to Gradle (or set `XPATH2_LOCAL=1`), which enables a composite build, see its `settings.gradle.kts`. The included build runs under the Gradle version of the consuming build, which has to be 9 or newer (the processor uses the Shadow plugin 9).

## Vendored `org.eclipse.wst.xml.xpath2.processor`

Upstream: [Eclipse WTP Source Editing](https://eclipse.googlesource.com/sourceediting/webtools.sourceediting.git) (`xpath/bundles/org.eclipse.wst.xml.xpath2.processor/`).

The pinned upstream commit is recorded in [`SOURCE_COMMIT`](./SOURCE_COMMIT).

Local changes to re-apply when re-vendoring:

- ICU4J (a 14 MB dependency) is replaced by the JDK, i.e. `Character` / `String` code point methods and `java.text.Normalizer` for `fn:normalize-unicode`.
- `io.appium.xpath2.DocumentLoader` and `io.appium.xpath2.JaxpFactories` are added (not part of upstream).
- `DynamicContextBuilder` does not load documents for `fn:doc` unless `withDocumentLoading(true)` is called (the upstream test harness, `AbstractPsychoPathTest`, opts in), so an expression cannot make the host process read arbitrary files or URIs.
- `DocumentBuilderFactory.newInstance()` and `DatatypeFactory.newInstance()` are replaced by `JaxpFactories` in `DOMBuilder`, `XercesLoader`, `DynamicContextAdapter`, `DynamicContextBuilder`, `Function` and `AnyType`. Android has no `DatatypeFactory` implementation, and a lookup could pick up the one of the host process.

### License and notice

This component is **PsychoPath XPath 2.0 Processor** from the Eclipse Web Tools Platform.

- **License:** [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/) (EPL-2.0). Source files retain their original copyright headers (`SPDX-License-Identifier: EPL-2.0`).
- **About / redistribution notice:** [`org.eclipse.wst.xml.xpath2.processor/about.html`](./org.eclipse.wst.xml.xpath2.processor/about.html) (upstream “About This Content” text, unchanged).
- **Bundle metadata:** [`plugin.properties`](./org.eclipse.wst.xml.xpath2.processor/plugin.properties), [`META-INF/MANIFEST.MF`](./org.eclipse.wst.xml.xpath2.processor/META-INF/MANIFEST.MF).

`about.html`, `plugin.properties`, and `META-INF/` are included in the built JAR, matching the upstream Eclipse bundle layout.

### Build the JAR

```bash
npm run build --workspace @appium/xpath2-processor   # ./gradlew shadowJar
```

The output (the shaded jar) is written to `build/libs/`.

## Upstream unit tests

Test **sources** (with local harness patches) live under [`org.eclipse.wst.xml.xpath2.processor.tests/src`](./org.eclipse.wst.xml.xpath2.processor.tests/src/).

Large test **fixtures** are not stored in git. They are sparse-cloned from the same upstream repository at `SOURCE_COMMIT` into `.upstream/webtools.sourceediting/` when you run tests:

- `xpath/tests/org.w3c.xqts.testsuite` — W3C XPath 2.0 vectors (`TestSources/`, `Queries/`, `ExpectedTestResults/`)
- `xpath/tests/org.eclipse.wst.xml.xpath2.processor.tests` — bug regression XML/XSD fixtures (`bugTestFiles/`, etc.)

Eclipse/OSGi resource loading is replaced with classpath lookup via `TestResourceBundles`.

### Run tests

```bash
npm run test:java --workspace @appium/xpath2-processor   # ./gradlew test shadedTest
```

The `fetchXpath2TestData` task runs automatically before `test` (requires network on first run). CI runs this in `xpath2-processor-ci.yml`.

This executes the upstream `AllPsychoPathTests` aggregate suite (8000+ cases) through `PsychoPathTestSuiteAdapter`.
