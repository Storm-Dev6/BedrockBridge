plugins {
    base
}

group = "io.bedrockbridge"
version = "0.1.0-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.14.4"
    distributionType = Wrapper.DistributionType.BIN
}
