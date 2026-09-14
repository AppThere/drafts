import com.android.build.api.dsl.LibraryExtension

// The optional spatial layer (appthere-drafts.md 2, "On the XR target").
//
// An Android *library*, not an application: XR is "the Android target with an optional spatial
// layer", so it is something :app-android can grow, not a separate app to install. It is also not
// Kotlin Multiplatform -- androidx.xr.compose is Android-only, which 2 calls out explicitly as a
// constraint to respect.
//
// :app-android deliberately does NOT depend on this module in Phase 0. XR is feature-flagged and
// is "not a launch requirement"; wiring the dependency now would make an alpha library a hard
// requirement of every Android build. Phase 12 connects it.

plugins {
    alias(libs.plugins.androidLibrary)
    id("drafts.quality")
}

configure<LibraryExtension> {
    namespace = "com.appthere.drafts.app.android.xr"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":app-shared"))
}
