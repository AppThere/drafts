plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))
            api(project(":core-parse-markdown"))
            api(project(":core-parse-fountain"))
            api(project(":core-serialise"))
        }
    }
}
