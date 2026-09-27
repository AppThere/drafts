import org.gradle.api.tasks.PathSensitivity

// Architecture assertions from engineering-conventions.md 5, as real tests.
//
// A JVM test-only module: Konsist parses Kotlin source as text, so it needs no Kotlin
// Multiplatform machinery and nothing here ships.

plugins {
    id("drafts.jvm")
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
    // Konsist resolves scopes from the project root, which it finds by walking up from the
    // working directory. Pin it so the tests behave the same from the IDE and from CI.
    systemProperty("konsist.projectRoot", rootDir.absolutePath)

    // Every Kotlin source in the repository is an input to these tests, and Gradle has no way to
    // know it: Konsist opens the files itself at run time, so nothing in the task's declared inputs
    // changes when a module gains code. Without this the task goes UP-TO-DATE and the architecture
    // assertions quietly stop running -- which is the exact failure ArchitectureRulesFixtureTest
    // was written to prevent, arriving by a different door. It was not theoretical: `check` reported
    // clean locally while a real violation sat in :platform-files, and the first CI run found it.
    inputs
        .files(
            fileTree(rootDir) {
                include("*/src/**/*.kt", "*/*/src/**/*.kt")
                exclude("**/build/**")
            },
        ).withPropertyName("repositorySources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
