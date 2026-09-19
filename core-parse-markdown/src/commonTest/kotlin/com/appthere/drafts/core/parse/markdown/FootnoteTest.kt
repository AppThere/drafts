package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.FootnoteRef
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Footnotes (`markdown-dialect.md` 4).
 *
 * Each test names the rule it covers, because this extension is almost entirely rules: where
 * definitions may appear, how labels match, what ordering the output takes, and what happens when
 * the two halves do not line up.
 */
class FootnoteTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `a reference becomes a footnote reference and the definition leaves the body`() {
        val document = parser.parse("Text with a ref.[^1]\n\n[^1]: The footnote body.\n")

        assertEquals(listOf("1"), document.footnotes.keys.toList())
        assertTrue(
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<FootnoteRef>()
                .size == 1,
            "Expected one footnote reference in ${document.blocks}",
        )
        assertEquals(
            1,
            document.blocks.size,
            "The definition should have left the block list: ${document.blocks}",
        )
    }

    @Test
    fun `the definition body keeps its content without the marker`() {
        val document = parser.parse("Ref.[^note]\n\n[^note]: The body text.\n")

        val body = document.footnotes.getValue("note").single() as Paragraph
        assertEquals("The body text.", body.plainText())
    }

    @Test
    fun `a definition may appear before the reference that uses it`() {
        // 4: "Definitions may appear anywhere in the document; they are collected in a first pass."
        val document = parser.parse("[^early]: Defined first.\n\nAnd referenced[^early] later.\n")

        assertEquals(listOf("early"), document.footnotes.keys.toList())
        assertEquals(
            1,
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<FootnoteRef>()
                .size,
        )
    }

    @Test
    fun `labels are case insensitive`() {
        // "Labels are case-insensitive and normalised the same way link labels are."
        val document = parser.parse("Ref[^FooBar]\n\n[^foobar]: Body.\n")

        val references = document.blocks.flatMap { it.inlinesOf() }.filterIsInstance<FootnoteRef>()
        assertEquals(1, references.size, "Label case should not prevent a match")
        assertEquals(listOf("foobar"), document.footnotes.keys.toList())
    }

    @Test
    fun `output order follows first reference not definition order`() {
        // 4 states this explicitly, and it is the one rule a naive implementation gets wrong by
        // simply collecting definitions in the order they appear.
        val document =
            parser.parse("First[^b] then[^a].\n\n[^a]: Body A.\n\n[^b]: Body B.\n")

        assertEquals(listOf("b", "a"), document.footnotes.keys.toList())
    }

    @Test
    fun `a reference with no definition stays literal text`() {
        // "A reference with no definition renders as literal text."
        val document = parser.parse("An orphan[^missing] reference.\n")

        assertTrue(
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<FootnoteRef>()
                .isEmpty(),
            "An undefined label must not become a footnote reference",
        )
        assertTrue(
            (document.blocks.single() as Paragraph).plainText().contains("[^missing]"),
            "The literal text was lost",
        )
    }

    @Test
    fun `an unreferenced definition is kept and is not an error`() {
        // "An unreferenced definition is not an error; it is simply not rendered."
        val document = parser.parse("Just prose.\n\n[^unused]: Nobody points here.\n")

        assertEquals(listOf("unused"), document.footnotes.keys.toList())
        assertEquals(1, document.blocks.size, "Only the prose paragraph should remain")
    }

    @Test
    fun `a definition does not count as a reference to itself`() {
        // Without this the footnote would appear in the output the moment it was defined.
        val document = parser.parse("[^solo]: Only defined.\n")

        assertTrue(
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<FootnoteRef>()
                .isEmpty(),
            "The definition's own label was read as a reference",
        )
    }

    @Test
    fun `the same footnote may be referenced twice`() {
        val document = parser.parse("One[^x] and two[^x].\n\n[^x]: Body.\n")

        assertEquals(
            2,
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<FootnoteRef>()
                .size,
        )
        assertEquals(listOf("x"), document.footnotes.keys.toList())
    }

    @Test
    fun `text either side of a reference survives`() {
        val document = parser.parse("Before[^x]after.\n\n[^x]: Body.\n")

        val paragraph = document.blocks.single() as Paragraph
        val rendered =
            paragraph.inlines.joinToString("") { inline ->
                if (inline is FootnoteRef) "<ref>" else listOf(inline).plainText()
            }

        assertEquals("Before<ref>after.", rendered)
    }

    @Test
    fun `inline footnotes are not supported`() {
        // 4: "Inline footnotes (^[text]) are not supported." They stay literal.
        val document = parser.parse("An inline ^[footnote] here.\n")

        assertTrue(
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<FootnoteRef>()
                .isEmpty(),
        )
        assertTrue((document.blocks.single() as Paragraph).plainText().contains("^[footnote]"))
    }

    @Test
    fun `a multi-paragraph body becomes several blocks`() {
        // markdown-dialect.md 4: "Definition bodies may contain block content when continuation
        // lines are indented." The indentation is what marks continuation -- CommonMark would
        // otherwise read it as a code block, which is exactly what used to happen here.
        val document =
            parser.parse("Ref.[^1]\n\n[^1]: First paragraph.\n\n    Second paragraph.\n")

        val body = document.footnotes.getValue("1")
        assertEquals(2, body.size, "Expected two blocks, got: $body")
        assertEquals("First paragraph.", (body[0] as Paragraph).plainText())
        assertEquals("Second paragraph.", (body[1] as Paragraph).plainText())
    }

    @Test
    fun `an indented continuation is not a code block`() {
        val document = parser.parse("Ref.[^1]\n\n[^1]: Body.\n\n    More body.\n")

        assertTrue(
            document.footnotes.getValue("1").none { it is CodeBlock },
            "Continuation lines were read as code: ${document.footnotes.getValue("1")}",
        )
    }

    @Test
    fun `a footnote body may contain block structure`() {
        val document =
            parser.parse("Ref.[^1]\n\n[^1]: Intro.\n\n    - one\n    - two\n")

        val body = document.footnotes.getValue("1")
        assertTrue(
            body.any { it is ListBlock },
            "Expected a list inside the footnote body, got: $body",
        )
    }

    @Test
    fun `a footnote body keeps inline markup`() {
        val document = parser.parse("Ref.[^1]\n\n[^1]: Some *emphasis* here.\n")

        assertEquals("Some emphasis here.", (document.footnotes.getValue("1").single() as Paragraph).plainText())
    }

    @Test
    fun `footnote body spans point into the original document`() {
        // The body is parsed dedented, on its own, so its offsets index that extracted string. They
        // are mapped back -- otherwise they would be quietly wrong rather than obviously broken.
        val source = "Ref.[^1]\n\n[^1]: First paragraph.\n\n    Second paragraph.\n"
        val body = parser.parse(source).footnotes.getValue("1")

        val second = requireNotNull((body[1] as Paragraph).source)
        assertTrue(
            source.substring(second.start.value, second.endExclusive.value).contains("Second paragraph"),
            "Span pointed at: '${source.substring(second.start.value, second.endExclusive.value)}'",
        )
    }

    @Test
    fun `the document body does not contain the footnote definition`() {
        val document = parser.parse("Prose.[^1]\n\n[^1]: Footnote.\n\n    Continued.\n")

        assertEquals(1, document.blocks.size, "Definition leaked into the document: ${document.blocks}")
    }

    @Test
    fun `a document with no footnotes has none`() {
        val document = parser.parse("Just a paragraph.\n")

        assertTrue(document.footnotes.isEmpty())
        assertEquals(1, document.blocks.size)
    }
}
