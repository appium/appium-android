import org.gradle.api.tasks.testing.Test
import java.io.File

fun runCommand(workingDir: File?, vararg command: String) {
    val exitCode = ProcessBuilder(*command)
        .directory(workingDir)
        .inheritIO()
        .start()
        .waitFor()
    check(exitCode == 0) { "'${command.joinToString(" ")}' exited with $exitCode" }
}

plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.shadow)
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("xpath2Version").get()

val xpath2Root = layout.projectDirectory.dir("org.eclipse.wst.xml.xpath2.processor")
val xpath2TestsRoot = layout.projectDirectory.dir("org.eclipse.wst.xml.xpath2.processor.tests")

val upstreamUrl =
    "https://eclipse.googlesource.com/sourceediting/webtools.sourceediting.git"
val upstreamCommit = layout.projectDirectory.file("SOURCE_COMMIT").asFile.readText().trim()
val upstreamRoot = layout.projectDirectory.dir(".upstream")
val upstreamCheckoutDir = upstreamRoot.dir("webtools.sourceediting")
val upstreamXqtsRoot =
    upstreamCheckoutDir.dir("xpath/tests/org.w3c.xqts.testsuite")
val upstreamProcessorTestsRoot =
    upstreamCheckoutDir.dir("xpath/tests/org.eclipse.wst.xml.xpath2.processor.tests")

fun isUpstreamCheckoutContentValid(): Boolean {
    val checkoutDir = upstreamCheckoutDir.asFile
    val xqtsMarker = upstreamXqtsRoot.file("TestSources/emptydoc.xml").asFile
    val expectedResults = upstreamXqtsRoot.dir("ExpectedTestResults").asFile
    val processorBugFiles =
        upstreamProcessorTestsRoot.dir("bugTestFiles").asFile

    if (!checkoutDir.isDirectory ||
        !xqtsMarker.isFile ||
        !expectedResults.isDirectory ||
        !processorBugFiles.isDirectory ||
        !File(checkoutDir, ".git/HEAD").isFile
    ) {
        return false
    }

    return try {
        ProcessBuilder("git", "-C", checkoutDir.absolutePath, "rev-parse", "--verify", "HEAD")
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: Exception) {
        false
    }
}

fun isUpstreamCheckoutValid(): Boolean {
    val commitMarker = File(upstreamCheckoutDir.asFile, ".commit")
    return isUpstreamCheckoutContentValid() &&
        commitMarker.isFile &&
        commitMarker.readText().trim() == upstreamCommit
}

fun org.gradle.api.Project.deleteUpstreamCheckout(logger: org.gradle.api.logging.Logger) {
    val checkoutDir = upstreamCheckoutDir.asFile
    val upstreamRootDir = upstreamRoot.asFile
    if (!checkoutDir.exists() && !upstreamRootDir.exists()) {
        return
    }

    logger.lifecycle("Removing upstream XPath2 test data at ${checkoutDir.absolutePath}")
    try {
        delete(checkoutDir)
        if (upstreamRootDir.exists() && upstreamRootDir.list().isNullOrEmpty()) {
            delete(upstreamRootDir)
        }
    } catch (e: Exception) {
        logger.warn("Gradle delete failed ({}), retrying with rm -rf", e.message)
        if (checkoutDir.exists()) {
            runCommand(null, "rm", "-rf", checkoutDir.absolutePath)
        }
        if (upstreamRootDir.exists() && upstreamRootDir.list().isNullOrEmpty()) {
            upstreamRootDir.delete()
        }
    }
}

val cleanXpath2Upstream = tasks.register("cleanXpath2Upstream") {
    group = "xpath2"
    description = "Remove sparse-cloned upstream XPath2 test fixtures."
    doLast {
        deleteUpstreamCheckout(logger)
    }
}

val fetchXpath2TestData = tasks.register("fetchXpath2TestData") {
    group = "xpath2"
    description =
        "Sparse-clone W3C XQTS and PsychoPath test fixtures from the pinned upstream commit."
    val commitMarker = upstreamCheckoutDir.file(".commit")
    val xqtsMarker = upstreamXqtsRoot.file("TestSources/emptydoc.xml")
    inputs.property("commit", upstreamCommit)
    outputs.file(commitMarker)
    outputs.file(xqtsMarker)

    onlyIf {
        if (isUpstreamCheckoutValid()) {
            false
        } else {
            if (upstreamCheckoutDir.asFile.exists()) {
                logger.lifecycle(
                    "Upstream XPath2 test data is missing or invalid; re-fetching from $upstreamUrl",
                )
            }
            true
        }
    }

    doFirst {
        deleteUpstreamCheckout(logger)
    }

    doLast {
        val checkoutDir = upstreamCheckoutDir.asFile
        logger.lifecycle(
            "Fetching XPath2 test data from $upstreamUrl @ $upstreamCommit",
        )
        checkoutDir.mkdirs()

        fun git(vararg args: String) = runCommand(checkoutDir, "git", *args)

        git("init", "-q")
        git("remote", "add", "origin", upstreamUrl)
        git("fetch", "-q", "--depth", "1", "origin", upstreamCommit)
        git("checkout", "-q", "FETCH_HEAD")
        git("sparse-checkout", "init", "--cone")
        git(
            "sparse-checkout",
            "set",
            "xpath/tests/org.eclipse.wst.xml.xpath2.processor.tests",
            "xpath/tests/org.w3c.xqts.testsuite",
        )

        if (!isUpstreamCheckoutContentValid()) {
            deleteUpstreamCheckout(logger)
            error(
                "XPath2 test data fetch completed but checkout validation failed. " +
                    "Try: ./gradlew cleanXpath2Upstream fetchXpath2TestData",
            )
        }

        commitMarker.asFile.writeText("$upstreamCommit\n")
        logger.lifecycle("XPath2 test data ready at ${checkoutDir.absolutePath}")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf(xpath2Root.dir("src")))
        }
        resources {
            setSrcDirs(
                listOf(
                    xpath2Root.dir("grammars"),
                    xpath2Root.dir("META-INF"),
                ),
            )
        }
    }
    test {
        java {
            setSrcDirs(listOf(xpath2TestsRoot.dir("src")))
            exclude(
                "**/XPath20TestPlugin.java",
                "**/FilteringPerformanceTest.java",
            )
        }
        resources {
            setSrcDirs(listOf(upstreamXqtsRoot, upstreamProcessorTestsRoot))
            exclude(
                "**/.settings/**",
                "**/.classpath",
                "**/.project",
                "**/META-INF/**",
                "**/build.properties",
                "**/pom.xml",
                "**/build.xml",
                "**/src/**/*.java",
                "**/XQTSCatalog.html",
                "**/ReportingResults/**",
                "**/TestSuiteDocumentation/**",
                "**/createXPath2*.xsl",
                "**/ExtractXPath2*.xsl",
                "**/JunitTest.java",
            )
        }
    }
}

// Bundled into the shaded jar (see shadowJar) instead of being exposed as dependencies
val bundled: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}
configurations.compileOnly { extendsFrom(bundled) }
configurations.testImplementation { extendsFrom(bundled) }

dependencies {
    bundled(libs.xercesimpl)
    bundled(libs.java.cup.runtime)

    testImplementation(sourceSets.main.get().output)
    testImplementation(libs.xmlunit)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    // Preserve upstream Eclipse bundle license notice and OSGi metadata (build.properties bin.includes).
    from(xpath2Root) {
        include("about.html", "plugin.properties")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // The published artifact is the shadow jar
    archiveClassifier = "plain"
}

val shadedPrefix = "io.appium.xpath2.shaded"

// Parts of the DOM API that Android lacks, but the Xerces DOM implementation needs
val relocatedDomPackages = listOf(
    "org.w3c.dom.events",
    "org.w3c.dom.traversal",
    "org.w3c.dom.ranges",
    "org.w3c.dom.html",
    "org.w3c.dom.css",
    "org.w3c.dom.stylesheets",
    "org.w3c.dom.views",
    "org.w3c.dom.xpath",
    "org.w3c.dom.bootstrap",
)
// Single classes of the core DOM and LS packages that Android lacks (the rest of them is provided by the platform)
val relocatedDomClasses = listOf(
    "org.w3c.dom.ElementTraversal",
    "org.w3c.dom.ls.LSLoadEvent",
    "org.w3c.dom.ls.LSProgressEvent",
    "org.w3c.dom.ls.LSSerializerFilter",
)

// Xerces and friends are bundled under a private package, so that they cannot clash with the copies
// of the application under test when the processor is loaded into its process. The classes that
// the Android platform (or the JDK) already provides are left out.
tasks.shadowJar {
    archiveClassifier = ""
    configurations = listOf(bundled)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // The license notice of the upstream Eclipse bundle, as in the plain jar
    from(xpath2Root) {
        include("about.html", "plugin.properties")
    }
    (listOf("org.apache.xerces", "org.apache.xml", "org.apache.html", "org.apache.wml", "java_cup") +
        relocatedDomPackages + relocatedDomClasses
        ).forEach { relocate(it, "$shadedPrefix.$it") }
    exclude(
        "javax/**",
        "org/xml/**",
        "org/apache/xmlcommons/**",
        // Would make JAXP factories of the application under test resolve to the bundled Xerces
        "META-INF/services/**",
        "META-INF/maven/**",
    )
    val relocatedClassPaths = relocatedDomClasses.map { it.replace('.', '/') }
    exclude {
        val path = it.relativePath.pathString
        path.endsWith(".class") &&
            (path.matches(Regex("org/w3c/dom/[^/]+\\.class")) || path.startsWith("org/w3c/dom/ls/")) &&
            relocatedClassPaths.none { prefix -> path.startsWith(prefix) }
    }
}

// Project dependencies (e.g. the composite build of uiautomator2-server) get the shaded jar as well
listOf(configurations.apiElements, configurations.runtimeElements).forEach { variant ->
    variant.configure {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
    }
}

val shadedTest: SourceSet by sourceSets.creating

dependencies {
    // Only the shaded jar, so that the test fails if anything is still taken from the original libraries
    "shadedTestImplementation"(files(tasks.shadowJar))
    "shadedTestImplementation"(libs.junit)
    "shadedTestRuntimeOnly"(libs.junit.vintage.engine)
    "shadedTestRuntimeOnly"(libs.junit.platform.launcher)
}

val shadedTestTask = tasks.register<Test>("shadedTest") {
    group = "verification"
    description = "Runs smoke tests against the shaded jar."
    testClassesDirs = shadedTest.output.classesDirs
    classpath = shadedTest.runtimeClasspath
    useJUnitPlatform()
    val shadedJar = tasks.shadowJar.flatMap { it.archiveFile }
    doFirst {
        systemProperty("shaded.jar", shadedJar.get().asFile.absolutePath)
    }
    testLogging {
        events("failed", "skipped")
    }
}

tasks.check {
    dependsOn(shadedTestTask)
}

tasks.compileTestJava {
    dependsOn(fetchXpath2TestData)
}

tasks.processTestResources {
    dependsOn(fetchXpath2TestData)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(xpath2TestsRoot.dir("src/org/eclipse/wst/xml/xpath2/processor/test/xml")) {
        into("org/eclipse/wst/xml/xpath2/processor/test/xml")
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(fetchXpath2TestData)
}

tasks.test {
    useJUnitPlatform()
    filter {
        includeTestsMatching("org.eclipse.wst.xml.xpath2.processor.test.PsychoPathTestSuiteAdapter")
    }
    maxHeapSize = "2g"
    testLogging {
        events("failed", "skipped")
    }
}

tasks.named("clean") {
    dependsOn(cleanXpath2Upstream)
}

publishing {
    publications {
        create<MavenPublication>("processor") {
            artifactId = "xpath2-processor"
            from(components["shadow"])
            artifact(tasks.named("sourcesJar"))
            pom {
                name = "xpath2-processor"
                description = "PsychoPath XPath 2.0 processor (Eclipse WTP) for Appium Android servers"
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
