plugins { id("bedrockbridge.java-conventions") }
dependencies {
    implementation(project(":protocol-session"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator)
}
