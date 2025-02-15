import org.gradle.api.tasks.testing.logging.TestLogEvent

// TODO: remove suppression after upgrading Gradle to 8.x
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    java
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.nexusPublish)
    alias(libs.plugins.shadow)
    alias(libs.plugins.defaults)
    alias(libs.plugins.metadata)
    alias(libs.plugins.javadocLinks)
    alias(libs.plugins.githubRelease)
    alias(libs.plugins.license)
    alias(libs.plugins.dependencyCheck)
    alias(libs.plugins.versions)

    /* Code Quality Plugins */
    jacoco
    pmd
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.forbiddenApis)

    id("com.hivemq.third-party-license-generator")
}


/* ******************** metadata ******************** */

group = "com.hivemq"
description = "HiveMQ CE is a Java-based open source MQTT broker that fully supports MQTT 3.x and MQTT 5"

metadata {
    readableName.set("HiveMQ Community Edition")
    organization {
        name.set("HiveMQ GmbH")
        url.set("https://www.hivemq.com/")
    }
    license {
        apache2()
    }
    developers {
        register("cschaebe") {
            fullName.set("Christoph Schaebel")
            email.set("christoph.schaebel@hivemq.com")
        }
        register("lbrandl") {
            fullName.set("Lukas Brandl")
            email.set("lukas.brandl@hivemq.com")
        }
        register("flimpoeck") {
            fullName.set("Florian Limpoeck")
            email.set("florian.limpoeck@hivemq.com")
        }
        register("sauroter") {
            fullName.set("Georg Held")
            email.set("georg.held@hivemq.com")
        }
        register("SgtSilvio") {
            fullName.set("Silvio Giebl")
            email.set("silvio.giebl@hivemq.com")
        }
    }
    github {
        org.set("hivemq")
        repo.set("hivemq-community-edition")
        issues()
    }
}


/* ******************** java ******************** */

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }

    withJavadocJar()
    withSourcesJar()
}


/* ******************** dependencies ******************** */

repositories {
    mavenCentral()
}

dependencies {
    api(libs.hivemq.extensionSdk)

    // netty
    implementation(libs.netty.buffer)
    implementation(libs.netty.codec)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.common)
    implementation(libs.netty.handler)
    implementation(libs.netty.transport)

    // logging
    implementation(libs.slf4j.api)
    implementation(libs.julToSlf4j)
    implementation(libs.logback.classic)

    // security
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)

    // persistence
    implementation(libs.rocksdb)
    implementation(libs.xodus.openApi) {
        exclude("org.jetbrains", "annotations")
    }
    implementation(libs.xodus.environment) {
        exclude("org.jetbrains", "annotations")
    }
    // override transitive dependencies of xodus that have security vulnerabilities
    constraints {
        implementation(libs.kotlin.stdlib)
        implementation(libs.apache.commonsCompress)
    }

    // config
    implementation(libs.jaxb.api)
    runtimeOnly(libs.jaxb.impl)

    // metrics
    api(libs.dropwizard.metrics)
    implementation(libs.dropwizard.metrics.jmx)
    runtimeOnly(libs.dropwizard.metrics.logback)
    implementation(libs.oshi)
    // net.java.dev.jna:jna (transitive dependency of com.github.oshi:oshi-core) is used in imports

    // dependency injection
    implementation(libs.guice) {
        exclude("com.google.guava", "guava")
    }
    implementation(libs.javax.annotation.api)
    // javax.inject:javax.inject (transitive dependency of com.google.inject:guice) is used in imports

    // common
    implementation(libs.apache.commonsIO)
    implementation(libs.apache.commonsLang)
    implementation(libs.guava) {
        exclude("org.checkerframework", "checker-qual")
        exclude("com.google.errorprone", "error_prone_annotations")
    }
    // com.google.code.findbugs:jsr305 (transitive dependency of com.google.guava:guava) is used in imports
    implementation(libs.zeroAllocationHashing)
    implementation(libs.jackson.databind)
    implementation(libs.jctools)

    /* primitive data structures */
    implementation(libs.eclipse.collections)
}


/* ******************** test ******************** */

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.equalsVerifier)
    testImplementation(libs.concurrentUnit)
    testImplementation(libs.shrinkwrap.api)
    testRuntimeOnly(libs.shrinkwrap.impl)
    testImplementation(libs.byteBuddy)
    testImplementation(libs.wiremock.jre8.standalone)
    testImplementation(libs.javassist)
    testImplementation(libs.awaitility)
    testImplementation(libs.stefanBirkner.systemRules) {
        exclude("junit", "junit-dep")
    }
}

tasks.test {
    minHeapSize = "128m"
    maxHeapSize = "2048m"
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "--add-opens",
        "java.base/java.lang=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.nio=ALL-UNNAMED",
        "--add-opens",
        "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens",
        "jdk.management/com.sun.management.internal=ALL-UNNAMED",
        "--add-exports",
        "java.base/jdk.internal.misc=ALL-UNNAMED"
    )

    val inclusions = rootDir.resolve("inclusions.txt")
    val exclusions = rootDir.resolve("exclusions.txt")
    if (inclusions.exists()) {
        include(inclusions.readLines())
    } else if (exclusions.exists()) {
        exclude(exclusions.readLines())
    }

    testLogging {
        events = setOf(TestLogEvent.STARTED, TestLogEvent.FAILED)
    }
}


/* ******************** distribution ******************** */

tasks.jar {
    manifest.attributes(
        "Implementation-Title" to "HiveMQ",
        "Implementation-Vendor" to metadata.organization.get().name.get(),
        "Implementation-Version" to project.version,
        "HiveMQ-Version" to project.version,
        "Main-Class" to "com.hivemq.HiveMQServer"
    )
}

tasks.shadowJar {
    mergeServiceFiles()
}

val hivemqZip by tasks.registering(Zip::class) {
    group = "distribution"

    val name = "hivemq-ce-${project.version}"

    archiveFileName.set("$name.zip")

    from("src/distribution") { exclude("**/.gitkeep") }
    from("src/main/resources/config.xml") { into("conf") }
    from("src/main/resources/config.xsd") { into("conf") }
    from(tasks.shadowJar) { into("bin").rename { "hivemq.jar" } }
    into(name)
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("-html5")

    include("com/hivemq/embedded/*")

    doLast {
        javaexec {
            classpath("gradle/tools/javadoc-cleaner-1.0.jar")
        }
    }

    doLast { // javadoc search fix for jdk 11 https://bugs.openjdk.java.net/browse/JDK-8215291
        copy {
            from(destinationDir!!.resolve("search.js"))
            into(temporaryDir)
            filter { line -> line.replace("if (ui.item.p == item.l) {", "if (item.m && ui.item.p == item.l) {") }
        }
        delete(destinationDir!!.resolve("search.js"))
        copy {
            from(temporaryDir.resolve("search.js"))
            into(destinationDir!!)
        }
    }
}


/* ******************** checks ******************** */

jacoco {
    toolVersion = "${property("jacoco.version")}"
}

pmd {
    toolVersion = "${property("pmd.version")}"
    sourceSets = listOf(project.sourceSets.main.get())
    isIgnoreFailures = true
    rulesMinimumPriority.set(3)
}

spotbugs {
    toolVersion.set("${property("spotbugs.version")}")
    ignoreFailures.set(true)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
}

dependencies {
    spotbugsPlugins(libs.findsecbugs.plugin)
}

dependencyCheck {
    analyzers.apply {
        centralEnabled = false
    }
    format = org.owasp.dependencycheck.reporting.ReportGenerator.Format.ALL
    scanConfigurations = listOf("runtimeClasspath")
    suppressionFile = "$projectDir/gradle/dependency-check/suppress.xml"
}

tasks.check { dependsOn(tasks.dependencyCheckAnalyze) }

forbiddenApis {
    bundledSignatures = setOf("jdk-system-out")
}

tasks.forbiddenApisMain {
    exclude("**/BatchedException.class")
    exclude("**/LoggingBootstrap.class")
}

tasks.forbiddenApisTest { enabled = false }


/* ******************** compliance ******************** */

license {
    header = file("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}

downloadLicenses {
    dependencyConfiguration = "runtimeClasspath"
}

tasks.updateThirdPartyLicenses {
    dependsOn(tasks.downloadLicenses)
    projectName.set("HiveMQ")
    dependencyLicense.set(tasks.downloadLicenses.get().xmlDestination.resolve("dependency-license.xml"))
    outputDirectory.set(layout.projectDirectory.dir("src/distribution/third-party-licenses"))
}


/* ******************** publishing ******************** */

publishing {
    publications {
        register<MavenPublication>("distribution") {
            artifact(hivemqZip)

            artifactId = "hivemq-community-edition"
        }

        register<MavenPublication>("embedded") {
            from(components["java"])

            artifactId = "hivemq-community-edition-embedded"
        }
    }
}

signing {
    val signKey: String? by project
    val signKeyPass: String? by project
    useInMemoryPgpKeys(signKey, signKeyPass)
    sign(publishing.publications["embedded"])
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

githubRelease {
    token(System.getenv("GITHUB_TOKEN"))
    tagName(project.version.toString())
    releaseAssets(hivemqZip)
    allowUploadToExisting(true)
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations.shadowRuntimeElements.get()) {
    skip()
}
