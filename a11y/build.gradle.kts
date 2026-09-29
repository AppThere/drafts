// Plain Kotlin Multiplatform, not the Compose convention it was given in Phase 0: what it holds so
// far -- what blocks are called, and what is announced -- is policy, and has nothing to compose.
// The Compose compiler refuses a module without the Compose runtime, and adding the runtime only to
// satisfy it would be a dependency nothing uses. When preference plumbing arrives (10.2's
// reduced motion from the OS), and needs Compose, this goes back.
plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // What a block is (the model) and what it is called (the strings), for 10.1's names.
            api(project(":core-model"))
            implementation(project(":i18n"))
        }
    }
}
