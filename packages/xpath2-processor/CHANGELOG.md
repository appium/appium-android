# Change Log

All notable changes to this project will be documented in this file.
See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [2.0.6](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@2.0.5...@appium/xpath2-processor@2.0.6) (2026-11-19)

### Miscellaneous Chores

* **org.eclipse.wst.xml.xpath2.processor:** update dependency org.apache.maven.plugins:maven-javadoc-plugin to v3 ([#153](https://github.com/appium/appium-android/issues/153)) ([2cf76c6](https://github.com/appium/appium-android/commit/2cf76c63e6ab7f485a8638af64774f26dfec1d9e))
* **org.eclipse.wst.xml.xpath2.processor:** update dependency org.apache.maven.plugins:maven-pmd-plugin to v3 ([#154](https://github.com/appium/appium-android/issues/154)) ([ff1593e](https://github.com/appium/appium-android/commit/ff1593e36d6142d4857ced0a0823a6764a366ded))


## [2.0.5](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@2.0.4...@appium/xpath2-processor@2.0.5) (2026-11-19)

### Miscellaneous Chores

* **xpath2-processor:** bump JUnit to 6.1.3 ([#152](https://github.com/appium/appium-android/issues/152)) ([023ebf7](https://github.com/appium/appium-android/commit/023ebf7fa95dc1a883b8875f12f9c51cc0115703))


## [2.0.4](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@2.0.3...@appium/xpath2-processor@2.0.4) (2026-11-19)

### Miscellaneous Chores

* **wrapper:** update gradle to v9.9.1 ([#147](https://github.com/appium/appium-android/issues/147)) ([ac49a3c](https://github.com/appium/appium-android/commit/ac49a3c6e46928b1874733b62826d38d9b41e653))


## [2.0.3](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@2.0.2...@appium/xpath2-processor@2.0.3) (2026-11-18)

### Miscellaneous Chores

* **uiautomator2-server:** bump server dependencies ([#138](https://github.com/appium/appium-android/issues/138)) ([2e66e88](https://github.com/appium/appium-android/commit/2e66e8826de34ca4f1c646340c27b41738cce080))


## [2.0.2](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@2.0.1...@appium/xpath2-processor@2.0.2) (2026-11-18)

### Miscellaneous Chores

* **org.eclipse.wst.xml.xpath2.processor:** update dependency org.apache.maven.plugins:maven-pmd-plugin to v2.7.1 ([#132](https://github.com/appium/appium-android/issues/132)) ([8cb9ab8](https://github.com/appium/appium-android/commit/8cb9ab8e45af8fe96c2df642ab2eebc43815d994))


## [2.0.1](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@2.0.0...@appium/xpath2-processor@2.0.1) (2026-11-18)

### Miscellaneous Chores

* **org.eclipse.wst.xml.xpath2.processor:** update dependency org.apache.maven.plugins:maven-javadoc-plugin to v2.10.4 ([#131](https://github.com/appium/appium-android/issues/131)) ([d0cdefc](https://github.com/appium/appium-android/commit/d0cdefc546c904f9bc9933c6bb884e01bbf94c36))


## [2.0.0](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@1.0.2...@appium/xpath2-processor@2.0.0) (2026-11-15)

### ⚠ BREAKING CHANGES

* **xpath2-processor:** org.apache.xerces, java_cup and xml-apis are no longer
available to the consumers of the processor. Use
io.appium.xpath2.DocumentLoader.parse(InputStream) to parse the XML
that expressions are evaluated against. The processor now needs Gradle 9, so
builds that include it (-PxpathLocal in uiautomator2-server) have to use
Gradle 9 or newer as well.
* **xpath2-processor:** fn:doc no longer loads documents with the default
DynamicContextBuilder; call withDocumentLoading(true) to restore it.

### Features

* **xpath2-processor:** bundle xerces and java-cup under a private package ([#121](https://github.com/appium/appium-android/issues/121)) ([7751f3c](https://github.com/appium/appium-android/commit/7751f3cbee072d909000cd15f09a894a1f38fe81))


## [1.0.2](https://github.com/appium/appium-android/compare/@appium/xpath2-processor@1.0.1...@appium/xpath2-processor@1.0.2) (2026-11-14)

### Bug Fixes

* **xpath2-processor:** drop the ICU4J dependency in favor of the JDK ([#118](https://github.com/appium/appium-android/issues/118)) ([bbf71b3](https://github.com/appium/appium-android/commit/bbf71b3fad6542cd98da7788e82a4e12736a6064))

### Code Refactoring

* **uiautomator2-server:** consume the xpath2-processor JitPack artifact ([#117](https://github.com/appium/appium-android/issues/117)) ([d234ae8](https://github.com/appium/appium-android/commit/d234ae89e7cb670a2b5a4c632255c89c5e306f9c))


## 1.0.1 (2026-11-13)

### Code Refactoring

* extract the vendored XPath2 processor into a shared xpath2-processor package ([#111](https://github.com/appium/appium-android/issues/111)) ([c1da3bb](https://github.com/appium/appium-android/commit/c1da3bb816cac6c0723dbf2c67ba255f06c58334))
