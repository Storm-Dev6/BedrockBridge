plugins { id("bedrockbridge.application-conventions") }

dependencies {
    implementation(project(":common"))
    implementation(project(":bedrock-auth"))
    implementation(project(":bedrock-codec"))
}

application { mainClass = "io.bedrockbridge.registry.generator.BdsProvenanceCli" }

tasks.register<JavaExec>("runLoopbackProbe") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.bedrockbridge.registry.generator.BdsLoopbackProbe"
}
