// A Compose module, for the one thing in it: appthere-drafts.md 11.1's strings, which live in
// Compose Multiplatform Resources and are read with `stringResource`. There is no UI here — only
// the words, and the generated `Res` class that reaches them.
plugins {
    id("drafts.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.components.resources)
            // The generated accessors are composables, so the compiler plugin needs the runtime.
            implementation(compose.runtime)
        }
    }
}

// `Res` lands in this package rather than one derived from the module name, so a string reads as
// `Res.string.untitled` from anywhere that depends on the strings.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.appthere.drafts.i18n.resources"
    generateResClass = always
}
