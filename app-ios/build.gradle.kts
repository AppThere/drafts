// The iOS host's Kotlin half (appthere-drafts.md 3: ":app-ios  SwiftUI/UIKit scene host").
//
// Apple targets only -- there is no jvm() or android target here, because this module exists to
// produce the framework that the Xcode project links against. The SwiftUI/UIKit scene host itself
// is Swift and lives in the Xcode project, which Phase 11 adds.
//
// These targets cannot be compiled anywhere but macOS: Kotlin/Native needs the Xcode toolchain.
// On Linux and Windows the targets are declared but their compile tasks cannot run, which is why
// CI runs the Apple half of `check` on a macOS runner. See .github/workflows/ci.yml.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("drafts.quality")
}

kotlin {
    // No iosX64 -- out of the target matrix. See drafts.kmp.gradle.kts for why.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Drafts"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":app-shared"))
        }
    }
}
