plugins { `kotlin-dsl` }

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.2.1")

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.io.tmpdir", temporaryDir)
}
