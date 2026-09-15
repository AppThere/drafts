plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))
        }
        commonTest.dependencies {
            // Round-trip is parse-then-serialise, so the tests need the parser. Test-only: the
            // serialiser itself must never depend on a parser, or the IR stops being the contract.
            implementation(project(":core-parse-markdown"))
        }
    }
}
