pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "BedrockBridge"

include(
    "application",
    "api",
    "bedrock-codec",
    "bedrock-common",
    "bedrock-login",
    "bedrock-packets",
    "bedrock-session",
    "common",
    "config",
    "network-core",
    "network-raknet",
    "observability",
    "packet-buffer",
    "packet-codec",
    "packet-pipeline",
    "packet-registry",
    "protocol-benchmarks",
    "protocol-common",
    "protocol-session",
    "session",
    "udp-transport",
)

includeBuild("build-logic")
