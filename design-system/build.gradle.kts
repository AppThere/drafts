plugins {
    id("drafts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.test)
        }

        // On a device, for the questions a host test cannot ask: what the platform's own font
        // fallback actually covers.
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.test.runner)
        }
    }
}

// The generated `Res` class lands in this package rather than one derived from the module name,
// so the fonts read as `Res.font.atkinson_next` from anywhere that depends on the design system.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.appthere.drafts.design.resources"
    generateResClass = always
}

// Compose Resources adds a copy-to-assets task per Android variant, and for the device-test
// variant it configures no output directory -- so the task fails validation and takes the whole
// device-test run with it.
//
// Disabling it is what lets device tests run at all, and the cost is that the bundled resources do
// not reach the test APK: with it disabled the merged device-test assets are empty. So a device
// test here can check the *platform's* fonts but not ours. `AndroidFontTest` says as much, and
// this should be revisited when the plugin fixes the task -- the assertion it is missing is the
// most valuable one in the project for Android packaging.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach {
    enabled = false
}
