plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":bedrock-packets"))
    api(project(":network-raknet"))
    api(project(":packet-codec"))
    api(project(":packet-registry"))
    api(project(":protocol-session"))
}
