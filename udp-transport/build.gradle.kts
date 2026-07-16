plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":network-core"))
    implementation(project(":packet-buffer"))
}
