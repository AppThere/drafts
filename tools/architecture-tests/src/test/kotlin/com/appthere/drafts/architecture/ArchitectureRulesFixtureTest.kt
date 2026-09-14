package com.appthere.drafts.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the architecture assertions actually fire.
 *
 * Phase 0's acceptance criteria ask that at least one Konsist assertion be shown failing against a
 * violating fixture. All six are, because the failure mode being guarded against is not "this one
 * assertion is broken" but "assertions over an empty repository are indistinguishable from
 * assertions that can never match" -- and that applies to every one of them equally.
 *
 * The fixtures live in `src/test/resources/fixtures/violating` as real `.kt` files that are never
 * compiled. Konsist parses Kotlin as text, so a fixture can be as wrong as it needs to be without
 * the compiler having an opinion.
 */
class ArchitectureRulesFixtureTest {
    @Test
    fun `fixtures are present and parsed`() {
        // If this fails, every other test in this class would pass vacuously -- which is exactly
        // the failure this class exists to prevent, so it is worth asserting directly.
        assertEquals(
            EXPECTED_FIXTURE_COUNT,
            fixtureFiles().size,
            "Expected $EXPECTED_FIXTURE_COUNT fixture files in $FIXTURE_DIR",
        )
    }

    @Test
    fun `compose in core-model is detected`() {
        val result = ArchitectureRules.coreModelDoesNotDependOnCompose(fixtureFiles())

        assertTrue(result.offenders.isNotEmpty(), "Expected the Compose import to be caught")
        assertTrue(
            result.offenders.any { it.endsWith("ComposeInCoreModel.kt") },
            "Expected ComposeInCoreModel.kt among: ${result.offenders}",
        )
    }

    @Test
    fun `jvm api in commonMain is detected`() {
        val result = ArchitectureRules.commonMainDoesNotUseJvmApis(fixtureFiles())

        assertTrue(
            result.offenders.any { it.endsWith("JvmApiInCommon.kt") },
            "Expected JvmApiInCommon.kt among: ${result.offenders}",
        )
    }

    @Test
    fun `dispatchers IO in commonMain is detected`() {
        val result = ArchitectureRules.commonMainDoesNotUseDispatchersIo(fixtureFiles())

        assertTrue(
            result.offenders.any { it.endsWith("DispatchersIoInCommon.kt") },
            "Expected DispatchersIoInCommon.kt among: ${result.offenders}",
        )
    }

    @Test
    fun `disk write outside platform-files is detected`() {
        val result = ArchitectureRules.onlyPlatformFilesWritesToDisk(fixtureFiles())

        assertTrue(
            result.offenders.any { it.endsWith("JvmApiInCommon.kt") },
            "Expected the java.nio.file usage to be caught: ${result.offenders}",
        )
    }

    @Test
    fun `xml by concatenation is detected`() {
        val result = ArchitectureRules.exportBackendsDoNotBuildXmlByConcatenation(fixtureFiles())

        assertTrue(
            result.offenders.any { it.endsWith("XmlByConcatenation.kt") },
            "Expected XmlByConcatenation.kt among: ${result.offenders}",
        )
    }

    @Test
    fun `misplaced Modifier parameter is detected`() {
        val result = ArchitectureRules.modifierIsFirstOptionalParameter(fixtureFiles())

        assertTrue(
            result.offenders.any { it.contains("ModifierNotFirstOptional.kt") },
            "Expected ModifierNotFirstOptional.kt among: ${result.offenders}",
        )
    }

    @Test
    fun `a rule does not fire on compliant source`() {
        // The other direction: a rule that matches everything is as useless as one that matches
        // nothing, and only this test would notice.
        val result = ArchitectureRules.coreModelDoesNotDependOnCompose(compliantFiles())

        assertTrue(
            result.holds,
            "Compliant fixtures must not be reported: ${result.offenders}",
        )
    }

    private fun fixtureFiles(): List<KoFileDeclaration> = Konsist.scopeFromDirectory(FIXTURE_DIR).files

    private fun compliantFiles(): List<KoFileDeclaration> = Konsist.scopeFromDirectory(COMPLIANT_DIR).files

    private companion object {
        const val FIXTURE_DIR = "tools/architecture-tests/src/test/resources/fixtures/violating"
        const val COMPLIANT_DIR = "tools/architecture-tests/src/test/resources/fixtures/compliant"
        const val EXPECTED_FIXTURE_COUNT = 5
    }
}
