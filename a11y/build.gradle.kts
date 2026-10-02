// The preference plumbing 10.2 asks for has arrived, so this is a Compose module again -- reading
// the operating system's reduced-motion setting needs a `Context` on Android and a composition to
// read it from, exactly as `isSystemInDarkTheme()` does.
plugins {
    id("drafts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // What a block is (the model) and what it is called (the strings), for 10.1's names.
            api(project(":core-model"))
            api(project(":i18n"))

            implementation(compose.runtime)
            // `LocalContext`, which is how the Android actual reaches the system settings.
            implementation(compose.ui)
        }
    }
}
