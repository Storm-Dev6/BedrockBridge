plugins { id("bedrockbridge.application-conventions") }
dependencies {
    implementation(project(":api"))
    implementation(project(":common"))
    implementation(project(":config"))
    implementation(project(":observability"))
    runtimeOnly(libs.logback.classic)
}
application { mainClass = "io.bedrockbridge.application.BridgeLauncher" }
