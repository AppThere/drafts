plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))

            // Fountain's shape rules, shared with the parser. Not a parser: deciding whether a line
            // reads as a scene heading is a fact about the syntax, and the one place both directions
            // can ask it so that they cannot disagree.
            implementation(project(":core-fountain"))
        }
        commonTest.dependencies {
            // Round-trip is parse-then-serialise, so the tests need the parsers. Test-only: the
            // serialiser itself must never depend on a parser, or the IR stops being the contract.
            implementation(project(":core-parse-markdown"))
            implementation(project(":core-parse-fountain"))
        }
    }
}
