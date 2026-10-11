plugins {
    id("drafts.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
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
            // A project window's binder shows and changes a project (projects-core).
            api(project(":project-model"))

            implementation(compose.runtime)
            // A project controller hands out StateFlows, so their runtime is part of the API.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.foundation)
            // Android's Back gesture closes an open panel rather than the activity.
            implementation(libs.compose.ui.backhandler)
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
