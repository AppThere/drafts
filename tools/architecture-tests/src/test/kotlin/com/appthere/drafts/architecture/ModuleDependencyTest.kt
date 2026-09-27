package com.appthere.drafts.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The dependency direction from `appthere-drafts.md` 3 and `engineering-conventions.md` 5:
 *
 *     app-* -> editor-* -> core-*, and :core-model depends on nothing.
 *
 * "Assert it; don't hope for it."
 *
 * This reads the Gradle module graph rather than Kotlin source, which is what makes it useful in
 * Phase 0. Konsist assertions over empty modules hold vacuously; this one has something real to
 * check from the first commit, because the wiring exists before the code does. It is also the test
 * that would catch the specific mistake AGENTS.md 1 warns about -- "putting code in the wrong
 * module is harder to undo later than it looks now" -- at the moment the dependency is added.
 */
class ModuleDependencyTest {
    @Test
    fun `every module declared in settings has a build file`() {
        val missing =
            declaredModules().filter { module ->
                !moduleDirectory(module).resolve("build.gradle.kts").isFile
            }

        assertTrue(missing.isEmpty(), "Modules declared in settings.gradle.kts with no build file: $missing")
    }

    @Test
    fun `core-model depends on nothing`() {
        // The root of the graph. Everything shares the IR, so anything core-model depends on
        // becomes a dependency of the parsers, the editor and all four export backends at once.
        assertEquals(
            emptySet(),
            dependenciesOf("core-model"),
            "core-model must depend on nothing (appthere-drafts.md 3)",
        )
    }

    @Test
    fun `core modules depend only on core modules`() {
        val violations =
            productModules()
                .filter { it.startsWith("core-") }
                .flatMap { module ->
                    dependenciesOf(module)
                        .filterNot { it.startsWith("core-") }
                        .map { "$module -> $it" }
                }

        assertTrue(violations.isEmpty(), "core-* may only depend on core-*: $violations")
    }

    @Test
    fun `editor modules do not depend on app modules`() {
        val violations =
            productModules()
                .filter { it.startsWith("editor-") }
                .flatMap { module ->
                    dependenciesOf(module)
                        .filter { it.startsWith("app-") }
                        .map { "$module -> $it" }
                }

        assertTrue(violations.isEmpty(), "editor-* must not depend on app-*: $violations")
    }

    @Test
    fun `cross-cutting and platform modules do not depend on editor or app modules`() {
        val violations =
            productModules()
                .filter { it in CROSS_CUTTING || it.startsWith("platform-") }
                .flatMap { module ->
                    dependenciesOf(module)
                        .filter { it.startsWith("editor-") || it.startsWith("app-") }
                        .map { "$module -> $it" }
                }

        assertTrue(
            violations.isEmpty(),
            "design-system, i18n, a11y and platform-* sit below the editor: $violations",
        )
    }

    @Test
    fun `the module graph is acyclic`() {
        val graph = productModules().associateWith { dependenciesOf(it) }
        val settled = mutableSetOf<String>()
        val onPath = mutableSetOf<String>()

        fun walk(
            module: String,
            path: List<String>,
        ) {
            if (module in settled) return
            if (!onPath.add(module)) {
                fail("Dependency cycle: ${(path + module).joinToString(" -> ")}")
            }
            graph[module].orEmpty().forEach { walk(it, path + module) }
            onPath.remove(module)
            settled.add(module)
        }

        graph.keys.forEach { walk(it, emptyList()) }
    }

    @Test
    fun `every module in the spec exists in the build`() {
        // appthere-drafts.md 3 is the normative list. If a module is dropped or renamed without
        // the spec changing, that is a finding, not a silent divergence (AGENTS.md 1).
        val missing = SPEC_MODULES - declaredModules().toSet()

        assertTrue(missing.isEmpty(), "Modules in appthere-drafts.md 3 missing from the build: $missing")
    }

    // --- reading the build ---------------------------------------------------------------------

    private fun dependenciesOf(module: String): Set<String> {
        val buildFile = moduleDirectory(module).resolve("build.gradle.kts")
        if (!buildFile.isFile) return emptySet()

        return PROJECT_REFERENCE
            .findAll(buildFile.readText())
            .map { it.groupValues[1] }
            .filterNot { it == module }
            .toSet()
    }

    private fun moduleDirectory(module: String): File {
        val relocated = RELOCATED[module]
        return if (relocated != null) repoRoot.resolve(relocated) else repoRoot.resolve(module)
    }

    private fun declaredModules(): List<String> =
        INCLUDE_LINE
            .findAll(repoRoot.resolve("settings.gradle.kts").readText())
            .map { it.groupValues[1] }
            .toList()

    /** Everything except the tooling projects, which are build infrastructure, not product. */
    private fun productModules(): List<String> = declaredModules().filterNot { it.startsWith("tools-") }

    private companion object {
        val repoRoot: File = File(System.getProperty("konsist.projectRoot") ?: ".").absoluteFile

        val PROJECT_REFERENCE = Regex("""project\(":([A-Za-z0-9-]+)"\)""")
        val INCLUDE_LINE = Regex("""^include\(":([A-Za-z0-9-]+)"\)""", RegexOption.MULTILINE)

        val RELOCATED =
            mapOf(
                "tools-detekt-rules" to "tools/detekt-rules",
                "tools-architecture-tests" to "tools/architecture-tests",
            )

        val CROSS_CUTTING = setOf("design-system", "i18n", "a11y")

        /** Transcribed from appthere-drafts.md 3, with :core-export-* expanded. */
        val SPEC_MODULES =
            setOf(
                "core-model",
                "core-parse-markdown",
                "core-parse-fountain",
                "core-serialise",
                "core-export-container",
                "core-export-xhtml",
                "core-export-odf",
                "core-export-ooxml",
                "editor-engine",
                "editor-ui",
                "design-system",
                "i18n",
                "a11y",
                "platform-files",
                "platform-windows",
                "platform-intents",
                "app-shared",
                "app-android",
                "app-android-xr",
                "app-ios",
                "app-desktop",
            )
    }
}
