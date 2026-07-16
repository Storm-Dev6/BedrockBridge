plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":bedrock-packets"))
    api(project(":packet-codec"))
    api(project(":packet-registry"))
}
