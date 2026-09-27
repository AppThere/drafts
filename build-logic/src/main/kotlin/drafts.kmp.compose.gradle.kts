// A Kotlin Multiplatform module that renders. Adds the Compose Multiplatform plugin and the
// Compose compiler on top of the baseline target set.
//
// No Compose *libraries* are declared here in Phase 0. The modules are empty, so a declared
// compose.runtime would be an unused dependency in every one of them, and dependency-analysis
// would be right to say so. Phase 1 and Phase 3 add the dependencies alongside the first code
// that needs them. What this plugin establishes now is that the plugin wiring itself works --
// which is the part that is awkward to retrofit.

plugins {
    id("drafts.kmp")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Compose Resources adds a copy-to-assets task per Android variant, and for the device-test variant
// it configures no output directory -- so the task fails Gradle's own property validation and takes
// whatever depends on it down with it. That includes `buildHealth`, which is how this surfaced: the
// first CI run failed on :a11y, a module with no resources of its own.
//
// Disabling it is what lets device tests run at all. The cost is that bundled resources do not
// reach the test APK, so a device test can check the platform's fonts but not ours --
// `AndroidFontTest` says as much. Revisit when the plugin fixes the task.
//
// Here rather than per-module because the task exists in every module this plugin is applied to,
// whether or not that module has any resources.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach {
    enabled = false
}
