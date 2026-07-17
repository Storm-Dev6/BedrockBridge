import net.ltgt.gradle.errorprone.errorprone

plugins { id("bedrockbridge.java-conventions") }
dependencies {
    implementation(project(":protocol-session"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.excludedPaths.set(".*/build/generated/.*")
}
