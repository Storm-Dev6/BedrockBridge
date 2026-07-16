plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":common"))
    api(libs.micrometer.core)
    api(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}
