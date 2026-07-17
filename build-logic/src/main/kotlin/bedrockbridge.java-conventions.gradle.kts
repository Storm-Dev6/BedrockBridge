import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `java-library`
    checkstyle
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

group = "io.bedrockbridge"
version = rootProject.version

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    errorprone(libs.findLibrary("errorprone-core").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(platform(libs.findLibrary("testcontainers-bom").get()))
    testImplementation(libs.findLibrary("testcontainers-junit").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    failFast = false
    systemProperty("java.io.tmpdir", temporaryDir)
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

spotless {
    java {
        googleJavaFormat("1.28.0")
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") { dependsOn("spotlessCheck") }
