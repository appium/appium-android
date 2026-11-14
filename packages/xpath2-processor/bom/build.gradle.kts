plugins {
    `java-platform`
    `maven-publish`
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("xpath2Version").get()

// Intentionally empty: the processor bundles its dependencies. The module exists because JitPack
// only serves a build under com.github.appium.appium-android:<artifactId> when it publishes several modules.
publishing {
    publications {
        create<MavenPublication>("bom") {
            artifactId = "xpath2-bom"
            from(components["javaPlatform"])
            pom {
                name = "xpath2-bom"
                description = "Placeholder BOM of xpath2-processor, which has no external dependencies"
                licenses {
                    license {
                        name = "EPL-2.0"
                        url = "https://www.eclipse.org/legal/epl-2.0/"
                    }
                }
            }
        }
    }
}
