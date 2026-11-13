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
    }
}
rootProject.name = "appium-uiautomator2-server"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
// Built from source for now; to be replaced with the released JitPack artifact.
includeBuild("../xpath2-processor") {
    dependencySubstitution {
        substitute(module("com.github.appium.appium-android:xpath2-processor")).using(project(":"))
    }
}
