plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":bedrock-auth"))
    api(project(":bedrock-crypto"))
    api(project(":bedrock-packets"))
    api(project(":protocol-session"))
}
