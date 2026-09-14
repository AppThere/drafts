plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))
            api(project(":core-export-package"))
        }
    }
}
