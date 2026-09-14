import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Quality gates applied to every module in the build.
//
// engineering-conventions.md 3 names the toolchain; this plugin is where it is actually wired.
// Nothing here relaxes a threshold -- thresholds live in config/detekt/detekt.yml so that there
// is exactly one place to read them and exactly one place a reviewer has to watch.

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("com.diffplug.spotless")
    // Applied per-module, not just at the root. Verified the hard way: with the plugin only on
    // the root project, `buildHealth` reports "No project health reports found" and no module has
    // a `projectHealth` task -- the gate looks configured and analyses nothing.
    id("com.autonomousapps.dependency-analysis")
}

private val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun lib(alias: String) = versions.findLibrary(alias).orElseThrow {
    IllegalStateException("Version catalog is missing library alias '$alias'")
}

private fun version(alias: String) = versions.findVersion(alias).orElseThrow {
    IllegalStateException("Version catalog is missing version alias '$alias'")
}.requiredVersion

configure<DetektExtension> {
    // One shared config. detekt merges this over its defaults rather than replacing them, so
    // rules we do not mention keep their upstream default.
    buildUponDefaultConfig = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))

    // Deliberately absent: `baseline = file("detekt-baseline.xml")`.
    //
    // engineering-conventions.md 3 permits exactly one baseline, spent at the moment tooling
    // meets existing code. Phase 0 puts the tooling in first precisely so that moment never
    // arrives. CI asserts the file does not exist (.github/workflows/ci.yml).

    // Kotlin Multiplatform lays source out as src/<sourceSet>/kotlin, which does not match
    // detekt's JVM-shaped defaults (src/main/kotlin, src/test/kotlin). Point it at the whole
    // source tree so every source set -- commonMain, jvmMain, androidMain, iosMain, and the
    // matching test sets -- is analysed by the one task.
    source.setFrom(layout.projectDirectory.dir("src"))

    parallel = true
    autoCorrect = false
}

dependencies {
    add("detektPlugins", lib("detekt-formatting"))
    add("detektPlugins", lib("composeRules-detekt"))

    // The project's own rules (engineering-conventions.md 4.1, 4.2, 4.4). A project cannot
    // supply rules to its own detekt run, so :tools-detekt-rules analyses itself with the
    // stock ruleset only.
    if (path != ":tools-detekt-rules") {
        add("detektPlugins", project(":tools-detekt-rules"))
    }
}

tasks.withType<Detekt>().configureEach {
    // Test fixtures under src/test/resources are deliberately-violating Kotlin, kept as .kt so
    // Konsist can parse them. Analysing them would fail the build on violations that are the
    // entire point of the files. This is engineering-conventions.md 2's
    // "**/test/**/fixtures/**  conformance corpora" exemption, applied at the source level.
    exclude("**/resources/**")

    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
        md.required.set(false)
    }
    jvmTarget = "17"
}

// The warn half of the engineering-conventions.md 2 table.
//
// Reports only -- it is deliberately NOT a dependency of `check`, because a warn crossing is
// information rather than a verdict (engineering-conventions.md 1). CI runs it separately and
// surfaces the output; AGENTS.md 5 item 4 is what consumes it.
val detektWarn = tasks.register<Detekt>("detektWarn") {
    group = "verification"
    description = "Reports size and complexity findings at the WARN thresholds. Never fails."

    buildUponDefaultConfig = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt-warn.yml"))
    setSource(layout.projectDirectory.dir("src"))

    ignoreFailures = true
    parallel = true
    autoCorrect = false
    jvmTarget = "17"

    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(true)
    }
}

// Keep the warn report from silently going stale: building the module refreshes it.
tasks.matching { it.name == "check" }.configureEach {
    finalizedBy(detektWarn)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**", "**/resources/**")
        ktlint(version("ktlint"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(version("ktlint"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}
