package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.HeadingStyle
import com.appthere.drafts.core.model.LinkReferenceDefinition
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.ListMarker
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.ThematicBreak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Block-level lowering: paragraphs, headings, quotes, code, lists, breaks. */
class BlockLoweringTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `a paragraph lowers to text`() {
        val paragraph = parser.parse("Hello world").blocks.single() as Paragraph

        assertEquals("Hello world", paragraph.plainText())
    }

    @Test
    fun `an ATX heading carries its level and style`() {
        val heading = parser.parse("### Third level").blocks.single() as Heading

        assertEquals(3, heading.level)
        assertEquals(HeadingStyle.ATX, heading.style)
        assertEquals("Third level", heading.inlines.plainText())
    }

    @Test
    fun `a setext heading is recorded as setext`() {
        // Canonical style for a re-serialised heading is ATX, so a heading written Setext has to
        // say so or it silently changes on save.
        val heading = parser.parse("Title\n=====").blocks.single() as Heading

        assertEquals(1, heading.level)
        assertEquals(HeadingStyle.SETEXT, heading.style)
    }

    @Test
    fun `heading text excludes the space after the marker`() {
        // ATX_CONTENT spans the separating space too; CommonMark does not count it as content.
        val heading = parser.parse("# Title").blocks.single() as Heading

        assertEquals("Title", heading.inlines.plainText())
    }

    @Test
    fun `a fenced code block keeps its language and fence`() {
        val code = parser.parse("```kotlin\nval x = 1\n```").blocks.single() as CodeBlock

        assertEquals("kotlin", code.language)
        assertEquals('`', code.fence?.char)
        assertEquals(3, code.fence?.length)
        assertTrue(code.text.contains("val x = 1"), "Actual: '${code.text}'")
    }

    @Test
    fun `an indented code block has no fence`() {
        val code = parser.parse("    indented\n").blocks.single() as CodeBlock

        assertNull(code.fence, "An indented block must stay indented on the way out")
    }

    @Test
    fun `a block quote nests its children`() {
        val quote = parser.parse("> quoted text").blocks.single() as BlockQuote

        assertEquals("quoted text", (quote.children.single() as Paragraph).plainText())
    }

    @Test
    fun `a thematic break lowers`() {
        assertTrue(parser.parse("---\n").blocks.single() is ThematicBreak)
    }

    @Test
    fun `a link definition is kept as a block`() {
        // Dropping these on save would break every reference that pointed at them.
        val definition =
            parser
                .parse("[ref]: /target\n")
                .blocks
                .filterIsInstance<LinkReferenceDefinition>()
                .single()

        assertEquals("ref", definition.label)
        assertEquals("/target", definition.href)
    }

    // --- lists -----------------------------------------------------------------------------------

    @Test
    fun `a bullet list records its marker`() {
        // A document written with `*` bullets must not come back with `-` bullets merely because
        // it was opened.
        val list = parser.parse("* one\n* two\n").blocks.single() as ListBlock

        assertFalse(list.ordered)
        assertEquals(ListMarker.ASTERISK, list.marker)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `a dash list records a dash marker`() {
        assertEquals(ListMarker.DASH, (parser.parse("- one\n- two\n").blocks.single() as ListBlock).marker)
    }

    @Test
    fun `an ordered list records its start and delimiter`() {
        val list = parser.parse("3. three\n4. four\n").blocks.single() as ListBlock

        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertEquals(ListMarker.PERIOD, list.marker)
    }

    @Test
    fun `a tight list is tight and a loose list is loose`() {
        // Decides whether items render wrapped in paragraphs, so it is semantic, not cosmetic.
        val tight = parser.parse("- one\n- two\n").blocks.single() as ListBlock
        val loose = parser.parse("- one\n\n- two\n").blocks.single() as ListBlock

        assertTrue(tight.tight, "A list with no blank lines is tight")
        assertFalse(loose.tight, "A blank line between items makes the list loose")
    }
}
