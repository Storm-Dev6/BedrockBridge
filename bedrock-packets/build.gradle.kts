plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":bedrock-common"))
    api(project(":protocol-common"))
    testImplementation(project(":packet-codec"))
}
