plugins { id("bedrockbridge.application-conventions") }

dependencies {
    implementation(project(":common"))
    implementation(project(":bedrock-auth"))
    implementation(project(":bedrock-codec"))
    implementation(project(":bedrock-crypto"))
    implementation(project(":network-raknet"))
}

application { mainClass = "io.bedrockbridge.registry.generator.BdsProvenanceCli" }

tasks.register<JavaExec>("runLoopbackProbe") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.bedrockbridge.registry.generator.BdsLoopbackProbe"
}

tasks.register<JavaExec>("runLiveObserver") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.bedrockbridge.registry.generator.BdsLiveObserverCli"
}
