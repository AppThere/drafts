import com.android.build.api.dsl.ApplicationExtension

// The Android application host (appthere-drafts.md 3: ":app-android  Activity, manifest, intent
// filters"). A thin shell over :app-shared -- the composition root lives there, not here.
//
// Note the absence of `org.jetbrains.kotlin.android`: since AGP 9.0 the Android plugin provides
// Kotlin support itself, and applying the standalone Kotlin Android plugin alongside it is a hard
// error rather than a warning. See https://kotl.in/gradle/agp-built-in-kotlin.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("drafts.quality")
}

private val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun sdk(alias: String) = versions.findVersion(alias).orElseThrow {
    IllegalStateException("Version catalog is missing version alias '$alias'")
}.requiredVersion.toInt()

configure<ApplicationExtension> {
    namespace = "com.appthere.drafts." + project.name.replace('-', '.')
    compileSdk = sdk("android-compileSdk")

    defaultConfig {
        applicationId = "com.appthere.drafts"
        minSdk = sdk("android-minSdk")
        targetSdk = sdk("android-targetSdk")
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    // NewApi, for this module's own code. The library modules lint themselves (drafts.kmp):
    // `checkDependencies` from here does not reach a KMP library's sources -- verified 2026-10-03,
    // it passed with `Path.of` put back in :platform-files.
    //
    // NewApi only, for now. Turning on the rest of lint is a separate decision with its own
    // findings to work through.
    lint {
        checkOnly += "NewApi"
        abortOnError = true
    }
}
