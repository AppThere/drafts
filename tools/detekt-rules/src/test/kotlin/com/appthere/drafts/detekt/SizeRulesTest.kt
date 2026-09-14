package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every rule in this module is tested by deliberately violating it and asserting that it fires.
 *
 * Phase 0's acceptance criteria call this out specifically, and the reason is worth keeping in
 * view: a custom rule that never matches is indistinguishable from a codebase with no violations.
 * Both look like a green build. The difference surfaces the first time someone relies on the rule
 * to catch something, which is months later and usually in review of a bug it should have caught.
 *
 * Each rule therefore gets both directions -- a violation that must be reported, and a clean case
 * that must not be. The clean case is the half that catches a rule matching far too much.
 */
class FileLengthTest {
    @Test
    fun `reports a file longer than the configured limit`() {
        val code =
            buildString {
                appendLine("package com.appthere.drafts")
                repeat(LONG_FILE_LINES) { appendLine("val property$it = $it") }
            }

        val findings = FileLength(TestConfig("maxLines" to SMALL_LIMIT)).lint(code)

        assertEquals(1, findings.size, "A file over the limit must be reported")
        assertTrue(
            findings.single().message.contains("significant lines"),
            "The message should say how long the file actually is",
        )
    }

    @Test
    fun `does not report a file under the limit`() {
        val code =
            """
            package com.appthere.drafts

            val a = 1
            val b = 2
            """.trimIndent()

        val findings = FileLength(TestConfig("maxLines" to SMALL_LIMIT)).lint(code)

        assertEquals(0, findings.size, "A short file must not be reported")
    }

    @Test
    fun `does not count imports blank lines or the licence header`() {
        // engineering-conventions.md 2 measures a file "excluding imports, licence header, and
        // blank lines". This file is well over the limit by raw line count and well under it by
        // the measure the spec actually specifies.
        val code =
            buildString {
                appendLine("/*")
                repeat(LICENCE_LINES) { appendLine(" * Licence line $it") }
                appendLine(" */")
                appendLine("package com.appthere.drafts")
                appendLine()
                repeat(IMPORT_LINES) { appendLine("import kotlin.collections.List$it") }
                appendLine()
                appendLine("val onlyRealLine = 1")
            }

        val findings = FileLength(TestConfig("maxLines" to SMALL_LIMIT)).lint(code)

        assertEquals(
            0,
            findings.size,
            "Imports, licence header and blank lines must not count toward file length",
        )
    }

    private companion object {
        const val SMALL_LIMIT = 10
        const val LONG_FILE_LINES = 25
        const val LICENCE_LINES = 20
        const val IMPORT_LINES = 20
    }
}

class LongComposableFunctionTest {
    @Test
    fun `reports a composable longer than the configured limit`() {
        val code =
            buildString {
                appendLine("@Composable")
                appendLine("fun Screen() {")
                repeat(BODY_LINES) { appendLine("    val value$it = $it") }
                appendLine("}")
            }

        val findings = LongComposableFunction(TestConfig("maxLines" to SMALL_LIMIT)).lint(code)

        assertEquals(1, findings.size, "A long composable must be reported")
    }

    @Test
    fun `does not report a non-composable function of the same length`() {
        // The generic complexity>LongMethod rule owns plain functions and excludes composables via
        // ignoreAnnotated. If this rule also fired on plain functions they would be reported twice,
        // at two different thresholds.
        val code =
            buildString {
                appendLine("fun compute() {")
                repeat(BODY_LINES) { appendLine("    val value$it = $it") }
                appendLine("}")
            }

        val findings = LongComposableFunction(TestConfig("maxLines" to SMALL_LIMIT)).lint(code)

        assertEquals(0, findings.size, "Plain functions belong to LongMethod, not to this rule")
    }

    @Test
    fun `does not report a short composable`() {
        val code =
            """
            @Composable
            fun Screen() {
                Text(stringResource(R.string.title))
            }
            """.trimIndent()

        val findings = LongComposableFunction(TestConfig("maxLines" to SMALL_LIMIT)).lint(code)

        assertEquals(0, findings.size, "A short composable must not be reported")
    }

    private companion object {
        const val SMALL_LIMIT = 5
        const val BODY_LINES = 12
    }
}

class ComposableParameterListTest {
    @Test
    fun `reports a composable with too many parameters`() {
        val code =
            """
            @Composable
            fun Editor(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
            }
            """.trimIndent()

        val findings = ComposableParameterList(TestConfig("maxParameters" to SMALL_LIMIT)).lint(code)

        assertEquals(1, findings.size, "A wide composable signature must be reported")
    }

    @Test
    fun `does not report a composable within the limit`() {
        val code =
            """
            @Composable
            fun Editor(text: String, modifier: Modifier = Modifier) {
            }
            """.trimIndent()

        val findings = ComposableParameterList(TestConfig("maxParameters" to SMALL_LIMIT)).lint(code)

        assertEquals(0, findings.size, "A narrow composable signature must not be reported")
    }

    @Test
    fun `does not report a plain function with many parameters`() {
        val code =
            """
            fun compute(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
            }
            """.trimIndent()

        val findings = ComposableParameterList(TestConfig("maxParameters" to SMALL_LIMIT)).lint(code)

        assertEquals(0, findings.size, "Plain functions belong to LongParameterList")
    }

    private companion object {
        const val SMALL_LIMIT = 3
    }
}
