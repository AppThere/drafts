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
        }
    }
}
