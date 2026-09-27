plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))
        }
    }
}
