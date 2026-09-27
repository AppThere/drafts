plugins {
    id("drafts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":editor-engine"))
            api(project(":design-system"))
            api(project(":i18n"))
            api(project(":a11y"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }

        commonTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        // The UI test drives a real composition, so it needs a Compose runtime for the host it runs
        // on. Only the JVM source set: `runComposeUiTest` on Android expects instrumentation.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
