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
