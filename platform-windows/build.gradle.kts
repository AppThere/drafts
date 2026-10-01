// A Compose module, because of the one thing in it that has to be read from a composition: where
// the hinge is on a foldable (appthere-drafts.md 6). On Android that answer lives behind a
// `Context` and arrives as a flow that changes while the window is open, which is a composable's
// job rather than a constructor argument's -- the same reason `:a11y` is a Compose module.
plugins {
    id("drafts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":platform-files"))

            // 6's `Fold` is layout vocabulary, defined where `WindowSize` is. This module reports
            // one; it does not decide what a layout does about it.
            api(project(":design-system"))

            implementation(compose.runtime)
            // `LocalContext` and `LocalDensity`: the Android actual needs a context to ask and a
            // density to answer in Dp rather than the pixels the platform reports.
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            // 6 names this library: "use Jetpack WindowManager's `FoldingFeature`".
            implementation(libs.androidx.window)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
