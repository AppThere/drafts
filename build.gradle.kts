plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.dependencyAnalysis)

    // Declared so the version is resolved once here and the convention plugins can apply them
    // to each module without repeating a version.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

// --- dependency-analysis -----------------------------------------------------------------------
//
// Phase 0 has empty modules that declare real dependencies on each other, because the deliverable
// is "empty modules with correct dependency directions". Those two facts are in tension: a module
// with no source cannot *use* the dependency it declares, so dependency-analysis correctly reports
// every one of them as unused.
//
// So the advice is reported, not enforced, for exactly as long as the modules are empty. Phase 1
// has begun filling them -- :core-model has real code now -- but the modules that *declare*
// dependencies (:editor-*, :app-*) are still empty, so flipping to "fail" today would fail on
// every one of them. The flip belongs in the change that gives :app-shared its composition root,
// which is the first point at which a declared dependency is actually used.
// This is a dated, scoped exception, not a permanent relaxation -- see the Phase 0 report.
dependencyAnalysis {
    issues {
        all {
            onUnusedDependencies { severity("warn") }
            onUsedTransitiveDependencies { severity("warn") }
            onIncorrectConfiguration { severity("warn") }
        }
    }
}

// --- Kover -------------------------------------------------------------------------------------
//
// engineering-conventions.md 3: "Coverage, reported not gated." No verification rule is
// configured, deliberately. A coverage gate on an empty repository would be either vacuous or a
// number someone games later.
dependencies {
    kover(project(":tools-detekt-rules"))
}

// --- Baseline assertion ------------------------------------------------------------------------
//
// engineering-conventions.md 3 permits exactly one detekt baseline, spent when tooling first
// meets existing code. Phase 0 exists so that moment never arrives, so the correct number of
// baseline files is zero and this task says so out loud.
//
// CI runs this too (.github/workflows/ci.yml), but having it in the build means a developer finds
// out locally rather than in review.
val assertNoDetektBaseline by tasks.registering {
    group = "verification"
    description = "Fails if a detekt baseline file exists anywhere in the repository."

    val repoRoot = layout.projectDirectory.asFile
    val reportFile = layout.buildDirectory.file("reports/baseline-check.txt")
    outputs.file(reportFile)

    doLast {
        val baselines = repoRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.isFile && it.name.startsWith("detekt-baseline") && it.extension == "xml" }
            .map { it.relativeTo(repoRoot).path }
            .toList()

        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(if (baselines.isEmpty()) "no baseline files\n" else baselines.joinToString("\n"))
        }

        if (baselines.isNotEmpty()) {
            error(
                buildString {
                    appendLine("A detekt baseline exists:")
                    baselines.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("engineering-conventions.md 3 permits exactly one baseline, and it is")
                    appendLine("not to be spent here. Phase 0 put the gates in before the code so that")
                    appendLine("the baseline could stay empty. Fix the findings instead.")
                },
            )
        }
    }
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Everything AGENTS.md 2 phase 1 runs, in one task."
    dependsOn(assertNoDetektBaseline)
    dependsOn(subprojects.map { "${it.path}:check" })
}
