pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.appium.appium-android") }
        }
    }
}
rootProject.name = "appium-uiautomator2-server"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
// -PxpathLocal (or XPATH2_LOCAL=1) builds the processor from source instead of using the JitPack artifact
if (providers.gradleProperty("xpathLocal").isPresent || providers.environmentVariable("XPATH2_LOCAL").isPresent) {
    includeBuild("../xpath2-processor") {
        dependencySubstitution {
            substitute(module("com.github.appium.appium-android:xpath2-processor")).using(project(":"))
        }
    }
}
