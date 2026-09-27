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

// `package` is a Java keyword, so the namespace drafts.kmp derives from the module name --
// com.appthere.drafts.core.export.package -- is not a legal package, and the Android R file task
// says so: "Package '...core.export.package.test' from AndroidManifest.xml is not a valid Java
// package name". It surfaced through `buildHealth`, which builds the device-test variant.
//
// The module keeps the name `export-pipeline.md` gives it. Only the namespace is changed, and the
// Kotlin sources here should use the same `packaging` segment when Phase 8 writes them.
//
// The module name itself is still an open question -- `:core-export-container` would match what the
// thing actually is, since EPUB calls it OCF and ODF/OOXML call it OPC -- but that is a spec change
// and not the build's call to make.
kotlin {
    androidLibrary {
        namespace = "com.appthere.drafts.core.export.packaging"
    }
}
