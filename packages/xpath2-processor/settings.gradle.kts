dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "xpath2-processor"

// JitPack serves a build with several published modules as
// com.github.appium.appium-android:<artifactId>, hence the BOM next to the processor.
include(":bom")
