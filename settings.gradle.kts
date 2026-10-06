rootProject.name = "Drafts"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Module graph per specifications/appthere-drafts.md 3.
//
// Dependency direction is app-* -> editor-* -> core-*, and :core-model depends on nothing.
// That direction is asserted, not merely intended: see
// tools/architecture-tests/src/test/kotlin/.../ModuleDependencyTest.kt.
//
// Modules are empty in Phase 0. That is deliberate -- the gates exist before the code they gate,
// so the detekt baseline starts empty and stays empty (engineering-conventions.md 3).

// --- core: pure commonMain, no platform code ---------------------------------------------
include(":core-model")

// Fountain's syntax, which both the parser and the serialiser have to agree on. Separate from
// both: a round-trip is only byte-identical if "looks like a scene heading" means exactly the
// same thing when reading and when writing, and two copies of that rule would drift.
include(":core-fountain")

include(":core-parse-markdown")
include(":core-parse-fountain")
include(":core-serialise")

// Export backends are write-only. appthere-drafts.md 3 writes these as the glob ":core-export-*";
// the concrete split follows export-pipeline.md "Module layout", renamed into the 3 scheme.
// :core-export-container holds the ZIP and XML writing shared by the three backends -- it exists in
// export-pipeline.md but has no counterpart in 3. See the Phase 0 report, item 5.
include(":core-export-container")
include(":core-export-xhtml")
include(":core-export-odf")
include(":core-export-ooxml")

// --- editor ---------------------------------------------------------------------------------
include(":editor-engine")
include(":editor-ui")

// --- cross-cutting -------------------------------------------------------------------------
// These were pure commonMain until the platform started answering questions they ask: :a11y reads
// the system's reduced-motion setting (10.2), and :i18n holds 11.1's strings, which are Compose
// resources and so are read from a composition. All three are still below the editor.
include(":design-system")
include(":i18n")
include(":a11y")

// --- platform: the only modules where expect/actual appears ---------------------------------
include(":platform-files")
include(":platform-windows")
include(":platform-intents")

// projects.md: a folder as a binder. Common logic over :platform-files.
include(":project-model")

// --- app ------------------------------------------------------------------------------------
include(":app-shared")
include(":app-android")
include(":app-android-xr")
include(":app-ios")
include(":app-desktop")

// --- tooling: not part of the 3 product graph ----------------------------------------------
// Custom detekt rules and the Konsist architecture assertions. These are build infrastructure
// that happens to be Gradle projects; they are excluded from the product dependency assertions.
include(":tools-detekt-rules")
include(":tools-architecture-tests")
project(":tools-detekt-rules").projectDir = file("tools/detekt-rules")
project(":tools-architecture-tests").projectDir = file("tools/architecture-tests")
