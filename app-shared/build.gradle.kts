plugins {
    id("drafts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":editor-ui"))
            api(project(":editor-engine"))
            api(project(":design-system"))
            api(project(":i18n"))
            api(project(":a11y"))
            api(project(":platform-files"))
            api(project(":platform-windows"))
            api(project(":platform-intents"))

            implementation(compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.foundation)
        }

        commonTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.kotlinx.coroutines.test)
        }

        // The panel is driven through a real composition, so it needs a runtime for the host it
        // runs on. JVM only: `runSkikoComposeUiTest` expects instrumentation on Android.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
