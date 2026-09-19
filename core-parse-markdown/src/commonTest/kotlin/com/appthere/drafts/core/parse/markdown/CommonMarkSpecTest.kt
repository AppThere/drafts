package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.parse.markdown.fixtures.SpecExample
import com.appthere.drafts.core.parse.markdown.fixtures.commonMarkExamples
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * All 652 examples from CommonMark 0.31.2, run from `commonTest` so every target is verified.
 *
 * What this measures is our **lowering**, not `intellij-markdown`'s conformance -- that is the
 * library's business and it is well established. A CST node dropped on the way to the IR, a span
 * sliced a character short, a marker token that survived filtering: each of those shows up here as
 * a diff, in bulk, against a corpus nobody has to invent.
 *
 * Some failures are expected and correct. This project implements a *dialect*, not bare
 * CommonMark: `~~x~~` is strikethrough here and literal text in the specification, a bare
 * `www.example.com` is linkified, and a `[^1]:` line is a footnote rather than a paragraph. Those
 * divergences are deliberate and documented in `markdown-dialect.md`; the report below separates
 * the count from the diagnosis so the two are not confused.
 */
class CommonMarkSpecTest {
    private val parser = MarkdownDocumentParser()
    private val renderer = HtmlRenderer()

    /**
     * The corpus, gated by a floor that only ever moves up.
     *
     * The target is 652/652 and this is not a way of settling for less. It is a ratchet: the number
     * is printed on every run, a regression fails the build, and raising [CONFORMANCE_FLOOR] is the
     * visible, reviewable act of banking an improvement.
     *
     * Worth distinguishing from a detekt baseline, which `engineering-conventions.md` 3 permits
     * exactly one of and which AGENTS.md 4 warns about at length. A baseline *silences* a list of
     * findings, and grows quietly. This is a single integer in a test, in the diff, next to a
     * printed report of everything still failing. Nothing is hidden; the failures are enumerated
     * every time the suite runs.
     */
    @Test
    fun `commonmark 0-31-2 conformance`() {
        val failures = commonMarkExamples.filterNot { it.run() }
        val passed = commonMarkExamples.size - failures.size

        println(report(failures))

        assertTrue(
            passed >= CONFORMANCE_FLOOR,
            "Conformance regressed: ${'$'}passed passing, floor is ${'$'}CONFORMANCE_FLOOR. " +
                "See the printed report for what broke.",
        )
        assertTrue(
            passed <= CONFORMANCE_FLOOR,
            "Conformance improved to ${'$'}passed. Raise CONFORMANCE_FLOOR to ${'$'}passed to bank it.",
        )
    }

    @Test
    fun `the corpus is complete`() {
        // A harness that silently ran zero examples would pass every other assertion in this file.
        assertTrue(
            commonMarkExamples.size == EXPECTED_EXAMPLES,
            "Expected $EXPECTED_EXAMPLES examples, found ${commonMarkExamples.size}",
        )
    }

    private fun SpecExample.run(): Boolean = runCatching { renderer.render(parser.parse(markdown)) }.getOrNull() == html

    private fun report(failures: List<SpecExample>): String {
        val total = commonMarkExamples.size
        val passed = total - failures.size

        return buildString {
            appendLine()
            appendLine("CommonMark 0.31.2: $passed/$total passing (${percent(passed, total)}%)")
            appendLine()
            appendLine("By section:")

            commonMarkExamples
                .groupBy { it.section }
                .forEach { (section, examples) ->
                    val sectionFailures = examples.count { it in failures }
                    val sectionPassed = examples.size - sectionFailures
                    val marker = if (sectionFailures == 0) "ok  " else "FAIL"
                    appendLine(
                        "  $marker $section: $sectionPassed/${examples.size}",
                    )
                }

            if (failures.isNotEmpty()) {
                appendLine()
                appendLine("Sample failures, up to $SAMPLE_SIZE per section:")
                failures.groupBy { it.section }.forEach { (_, sectionFailures) ->
                    sectionFailures.take(SAMPLE_SIZE).forEach { appendLine(it.diff()) }
                }
            }
        }
    }

    private fun SpecExample.diff(): String {
        val actual = runCatching { renderer.render(parser.parse(markdown)) }
        return buildString {
            appendLine("  --- example $number ($section)")
            appendLine("      markdown: ${markdown.visible()}")
            appendLine("      expected: ${html.visible()}")
            appendLine(
                "      actual:   " +
                    actual.fold({ it.visible() }, { "threw ${it::class.simpleName}: ${it.message}" }),
            )
        }
    }

    private fun String.visible(): String = replace("\n", "\\n").replace("\t", "\\t").take(TRUNCATE)

    private fun percent(
        part: Int,
        whole: Int,
    ): Int = if (whole == 0) 0 else part * PERCENT / whole

    private companion object {
        const val EXPECTED_EXAMPLES = 652

        /**
         * Examples currently passing. Target is [EXPECTED_EXAMPLES]; this may only be raised.
         *
         * What is left is understood rather than mysterious -- see the Phase 1 report. The large
         * remaining groups are backslash escapes, entity references and tab expansion, all of which
         * `intellij-markdown` performs when generating HTML rather than in the tree, so the work
         * has to happen in our lowering.
         */
        const val CONFORMANCE_FLOOR = 463
        const val SAMPLE_SIZE = 2
        const val TRUNCATE = 120
        const val PERCENT = 100
    }
}
