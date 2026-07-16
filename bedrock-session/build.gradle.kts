plugins { id("bedrockbridge.java-conventions") }
dependencies {
    api(project(":bedrock-codec"))
    api(project(":bedrock-login"))
    api(project(":network-core"))
    api(project(":session"))
}
