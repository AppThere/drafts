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
}
