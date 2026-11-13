plugins {
    `java-platform`
    `maven-publish`
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("xpath2Version").get()

dependencies {
    constraints {
        api(libs.xercesimpl)
        api(libs.icu4j)
        api(libs.java.cup.runtime)
    }
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            artifactId = "xpath2-bom"
            from(components["javaPlatform"])
            pom {
                name = "xpath2-bom"
                description = "Versions of the runtime dependencies of xpath2-processor"
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
