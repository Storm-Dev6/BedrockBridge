import org.gradle.jvm.tasks.Jar

plugins { id("bedrockbridge.application-conventions") }

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))
    implementation(project(":config"))
    implementation(project(":bedrock-registry-generator"))
    implementation(project(":bedrock-session"))
    implementation(project(":packet-buffer"))
    implementation(project(":udp-transport"))
    implementation(project(":observability"))
    runtimeOnly(libs.logback.classic)
}
application { mainClass = "io.bedrockbridge.application.BridgeLauncher" }

val fatJar = tasks.register<Jar>("fatJar") {
    archiveBaseName.set("BedrockBridge")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") { dependsOn(fatJar) }
