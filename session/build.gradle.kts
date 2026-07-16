plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":network-core"))
    api(project(":network-raknet"))
    api(project(":udp-transport"))
}
