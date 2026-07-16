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
