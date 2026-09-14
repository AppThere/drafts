// The desktop host (appthere-drafts.md 3: ":app-desktop  JVM main, file associations").
//
// A plain JVM module rather than a multiplatform one: macOS, Windows and Linux are all the same
// Compose Desktop JVM target (2, target matrix), so there is nothing here to be multiplatform
// about. It consumes :app-shared, which resolves to that module's jvm variant.
//
// No `compose.desktop.application { mainClass = ... }` block yet: that names an entry point, and
// Phase 0 has no code to name. Phase 5 (Desktop alpha) adds it along with main().

plugins {
    id("drafts.jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app-shared"))
}
