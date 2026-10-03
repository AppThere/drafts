plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))

            // FountainKeywords is in this module's public API -- 11.3 makes the keywords a setting --
            // so it is api rather than implementation.
            api(project(":core-fountain"))
        }
    }
}
