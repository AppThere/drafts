// A project (projects.md): a folder of the reader's own, its tree, and -- in later steps -- its
// order, metadata and reconciliation. Common logic over :platform-files' listing, which is the only
// thing that looks at the disk.
plugins {
    id("drafts.kmp")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":platform-files"))
            // A document's kind is its extension's (projects.md 5), decided once in 9.1's terms.
            api(project(":platform-intents"))
            implementation(libs.ktoml.core)
            // project.toml's model is @Serializable and public, so its runtime is part of the API.
            api(libs.kotlinx.serialization.core)
            // The cache's index (projects.md 9): derived data, disposable, so JSON like the sessions.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
