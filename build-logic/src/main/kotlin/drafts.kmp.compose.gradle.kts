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
