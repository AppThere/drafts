plugins {
    id("drafts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // 10.2's preference plumbing: the system's reduced-motion setting, which only the
            // platform can answer. `:a11y` sits alongside this one and below the editor, so this
            // is a sideways edge rather than a new layer.
            implementation(project(":a11y"))
            implementation(compose.runtime)
            implementation(compose.ui)
            // For `isSystemInDarkTheme`, which is how 5.5's "system" theme knows what the system is.
            implementation(compose.foundation)
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
