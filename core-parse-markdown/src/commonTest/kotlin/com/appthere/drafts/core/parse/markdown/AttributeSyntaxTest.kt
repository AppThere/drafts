package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Attribute syntax and automatic heading ids (`markdown-dialect.md` 7).
 *
 * The other fixture set the plan assigns to us. The rule that generates the most cases is the one
 * about what *isn't* an attribute block: generic block attributes are off, so braces after a
 * paragraph are prose, and a malformed block is text rather than an error.
 */
class AttributeSyntaxTest {
    private val parser = MarkdownDocumentParser()

    // --- headings --------------------------------------------------------------------------------

    @Test
    fun `a heading takes an id and a class from its attribute block`() {
        val heading = parser.parse("## Heading text {#custom-id .highlight}\n").blocks.single() as Heading

        assertEquals("custom-id", heading.attrs.id)
        assertEquals(listOf("highlight"), heading.attrs.classes)
        assertEquals("Heading text", heading.inlines.plainText(), "The block must leave the text")
    }

    @Test
    fun `key value attributes are read`() {
        val heading = parser.parse("# T {data-role=note width=400}\n").blocks.single() as Heading

        assertEquals("note", heading.attrs["data-role"])
        assertEquals("400", heading.attrs["width"])
    }

    @Test
    fun `a quoted value may contain spaces`() {
        // The form exists for exactly this; splitting on whitespace naively would break it.
        val heading = parser.parse("""# T {title="two words"}""" + "\n").blocks.single() as Heading

        assertEquals("two words", heading.attrs["title"])
    }

    @Test
    fun `a heading id is generated when none is given`() {
        // 7: automatic heading ids are on, using GitHub's algorithm.
        val heading = parser.parse("## Hello, World!\n").blocks.single() as Heading

        assertEquals("hello-world", heading.attrs.id)
    }

    @Test
    fun `a generated id keeps the words inside a code span`() {
        // GitHub reads the heading as a reader does. "Using `foo`" is "Using foo", so its id is
        // `using-foo` -- this used to come out as `using-`, the code dropped.
        val heading = parser.parse("## Using `foo`\n").blocks.single() as Heading

        assertEquals("using-foo", heading.attrs.id)
    }

    @Test
    fun `generated ids preserve non-Latin scripts`() {
        // "Unicode preserved" -- a heading in Japanese gets an id in Japanese, not an empty string.
        val heading = parser.parse("## 日本語の見出し\n").blocks.single() as Heading

        assertEquals("日本語の見出し", heading.attrs.id)
    }

    @Test
    fun `duplicate headings get suffixed ids`() {
        val document = parser.parse("# Intro\n\n# Intro\n\n# Intro\n")

        assertEquals(
            listOf("intro", "intro-1", "intro-2"),
            document.blocks.filterIsInstance<Heading>().map { it.attrs.id },
        )
    }

    @Test
    fun `an explicit id overrides the generated one`() {
        // 7 states this, and Attributes.plus implements it: later wins for id.
        val heading = parser.parse("## Heading text {#mine}\n").blocks.single() as Heading

        assertEquals("mine", heading.attrs.id)
    }

    @Test
    fun `a generated id does not collide with an explicit one used later`() {
        // Explicit ids are claimed before any are generated, so the first heading cannot take a
        // name the second one asked for by name.
        val ids =
            parser
                .parse("# Intro\n\n# Something else {#intro}\n")
                .blocks
                .filterIsInstance<Heading>()
                .map { it.attrs.id }

        assertEquals(2, ids.distinct().size, "Ids collided: $ids")
    }

    // --- images ----------------------------------------------------------------------------------

    @Test
    fun `an attribute block on the line after a standalone image attaches to it`() {
        val paragraph = parser.parse("![Alt](/img.png)\n{.rounded width=400}\n").blocks.single() as Paragraph

        val image = paragraph.inlines.single() as Image
        assertEquals(listOf("rounded"), image.attrs.classes)
        assertEquals("400", image.attrs["width"])
    }

    @Test
    fun `the attribute block is removed from the paragraph`() {
        val paragraph = parser.parse("![Alt](/img.png)\n{.rounded}\n").blocks.single() as Paragraph

        assertEquals(1, paragraph.inlines.size, "Only the image should remain: ${paragraph.inlines}")
    }

    // --- what is not an attribute block ----------------------------------------------------------

    @Test
    fun `a block after an ordinary paragraph is literal text`() {
        // 7: "Generic block attributes are off -- an attribute block on an arbitrary paragraph is
        // literal text."
        val paragraph = parser.parse("Some prose\n{.highlight}\n").blocks.single() as Paragraph

        assertTrue(
            paragraph.plainText().contains("{.highlight}"),
            "The braces should still be visible: ${paragraph.plainText()}",
        )
    }

    @Test
    fun `a malformed block is literal text and not an error`() {
        // 7: "Malformed attribute blocks are literal text, never a parse error."
        val heading = parser.parse("## Heading {not valid syntax}\n").blocks.single() as Heading

        assertEquals("Heading {not valid syntax}", heading.inlines.plainText())
        assertEquals("heading-not-valid-syntax", heading.attrs.id, "Id comes from the literal text")
    }

    @Test
    fun `an unclosed quote makes the block malformed`() {
        val heading = parser.parse("""# T {title="unclosed}""" + "\n").blocks.single() as Heading

        assertTrue(heading.inlines.plainText().contains("{title="), "Should be literal text")
    }

    @Test
    fun `braces mid-heading are prose`() {
        // The block must be "the last thing on the heading line".
        val heading = parser.parse("## A {brace} in the middle\n").blocks.single() as Heading

        assertEquals("A {brace} in the middle", heading.inlines.plainText())
    }
}
