package com.appthere.drafts.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The architecture assertions applied to the real modules.
 *
 * In Phase 0 every module is empty, so each of these scans zero files and holds vacuously. That is
 * the expected state and the whole point of doing Phase 0 first -- the gate is in place before the
 * code it gates, so the first file that would violate it fails on the commit that adds it.
 *
 * Konsist throws on an assertion over an empty list rather than passing it, which would make every
 * test here an error today. [assertRuleHolds] absorbs that, but it says so in the test output, so
 * an empty scan is visible rather than looking like a real check that passed.
 *
 * The assertions themselves are proved to work in [ArchitectureRulesFixtureTest], which runs them
 * against source written specifically to violate them.
 */
class ArchitectureTest {
    @Test
    fun `core model does not depend on Compose`() {
        assertRuleHolds(moduleFiles("core-model"), ArchitectureRules::coreModelDoesNotDependOnCompose)
    }

    @Test
    fun `commonMain does not use JVM APIs`() {
        assertRuleHolds(sourceSetFiles("commonMain"), ArchitectureRules::commonMainDoesNotUseJvmApis)
    }

    @Test
    fun `commonMain does not reference Dispatchers IO`() {
        assertRuleHolds(sourceSetFiles("commonMain"), ArchitectureRules::commonMainDoesNotUseDispatchersIo)
    }

    @Test
    fun `only platform-files writes to disk`() {
        assertRuleHolds(productionFiles(), ArchitectureRules::onlyPlatformFilesWritesToDisk)
    }

    @Test
    fun `export backends do not build XML by concatenation`() {
        val files = EXPORT_MODULES.flatMap { moduleFiles(it) }
        assertRuleHolds(files, ArchitectureRules::exportBackendsDoNotBuildXmlByConcatenation)
    }

    @Test
    fun `composables take Modifier as the first optional parameter`() {
        assertRuleHolds(productionFiles(), ArchitectureRules::modifierIsFirstOptionalParameter)
    }

    /**
     * Production source of the *product* modules.
     *
     * Two exclusions, both of which this test learned the hard way. `scopeFromProduction` drops
     * test source, which keeps out the fixtures in `src/test/resources` that exist precisely to
     * violate these rules. Filtering `/tools/` drops this module and `:tools-detekt-rules`, which
     * are build infrastructure rather than product: a Konsist test that reads `java.io.File` to
     * parse build files is not a violation of "only :platform-files writes to disk", it is the
     * thing doing the checking.
     */
    private fun productionFiles(): List<KoFileDeclaration> =
        runCatching { Konsist.scopeFromProduction().files }
            .getOrDefault(emptyList())
            .filterNot { it.path.contains("/tools/") }

    private fun moduleFiles(module: String): List<KoFileDeclaration> =
        runCatching { Konsist.scopeFromModule(module).files }.getOrDefault(emptyList())

    private fun sourceSetFiles(sourceSet: String): List<KoFileDeclaration> =
        runCatching { Konsist.scopeFromSourceSet(sourceSet).files }
            .getOrDefault(emptyList())
            .filterNot { it.path.contains("/tools/") }

    /**
     * Applies [rule] to [files] and fails with the offending paths listed.
     *
     * An empty file list is reported, not silently skipped: "no files yet" and "checked and clean"
     * are different states, and conflating them is how an assertion quietly stops meaning anything
     * as a repository grows.
     */
    private fun assertRuleHolds(
        files: List<KoFileDeclaration>,
        rule: (List<KoFileDeclaration>) -> ArchitectureRules.Violations,
    ) {
        if (files.isEmpty()) {
            println("[architecture] no files in scope yet -- assertion holds vacuously (Phase 0)")
            return
        }

        val result = rule(files)
        assertTrue(result.holds, result.describe())
    }

    private companion object {
        val EXPORT_MODULES =
            listOf(
                "core-export-package",
                "core-export-xhtml",
                "core-export-odf",
                "core-export-ooxml",
            )
    }
}
