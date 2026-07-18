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
