import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

class JavaConventionsPluginFunctionalTest {
    @Test
    fun `plugin resolves dependencies from the target version catalog`(
        @TempDir(cleanup = CleanupMode.NEVER) projectDir: Path
    ) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                repositories { mavenCentral() }
            }
            rootProject.name = "conventions-test"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("bedrockbridge.java-conventions") }
            """.trimIndent()
        )
        projectDir.resolve("gradle").createDirectories()
        projectDir.resolve("gradle/libs.versions.toml").writeText(
            """
            [versions]
            errorprone = "2.42.0"
            junit = "5.13.4"
            testcontainers = "1.21.3"

            [libraries]
            errorprone-core = { module = "com.google.errorprone:error_prone_core", version.ref = "errorprone" }
            junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
            junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
            junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
            testcontainers-bom = { module = "org.testcontainers:testcontainers-bom", version.ref = "testcontainers" }
            testcontainers-junit = { module = "org.testcontainers:junit-jupiter" }
            """.trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(
                    "dependencies",
                    "--configuration",
                    "testRuntimeClasspath",
                    "--stacktrace",
                )
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":dependencies")?.outcome)
    }
}
