package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.CodeFence
import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.EmphasisDelimiter
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.HeadingStyle
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.LinkForm
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.ListMarker
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.ThematicBreak
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Writing from the IR alone, with no source to fall back on.
 *
 * This is the path an edited or newly created block takes. The round-trip tests cannot reach it --
 * they always have the original bytes -- so without these the canonical writer would be entirely
 * untested while every test stayed green.
 */
class CanonicalWriteTest {
    private val serialiser = MarkdownSerialiser()

    @Test
    fun `a synthesised paragraph writes its text`() {
        assertWrites("Hello world", Paragraph(inlines = listOf(Text("Hello world"))))
    }

    @Test
    fun `a heading writes as ATX by default`() {
        // The IR's defaults are the contract's canonical style, so new content lands on it without
        // the writer needing a separate opinion.
        assertWrites("## Section", Heading(level = 2, inlines = listOf(Text("Section"))))
    }

    @Test
    fun `a setext heading writes its rule`() {
        assertWrites(
            "Title\n=====",
            Heading(level = 1, inlines = listOf(Text("Title")), style = HeadingStyle.SETEXT),
        )
    }

    @Test
    fun `emphasis uses the recorded delimiter`() {
        assertWrites(
            "_word_",
            Paragraph(
                inlines =
                    listOf(
                        Emphasis(
                            strong = false,
                            children = listOf(Text("word")),
                            delimiter = EmphasisDelimiter.UNDERSCORE,
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `strong emphasis doubles the delimiter`() {
        assertWrites(
            "**word**",
            Paragraph(inlines = listOf(Emphasis(strong = true, children = listOf(Text("word"))))),
        )
    }

    @Test
    fun `a code span uses the recorded backtick run`() {
        assertWrites(
            "``has ` backtick``",
            Paragraph(inlines = listOf(CodeSpan(text = "has ` backtick", backtickCount = 2))),
        )
    }

    @Test
    fun `a bullet list uses the recorded marker`() {
        assertWrites(
            "* one\n* two",
            ListBlock(
                ordered = false,
                items = listOf(listOf(paragraph("one")), listOf(paragraph("two"))),
                marker = ListMarker.ASTERISK,
            ),
        )
    }

    @Test
    fun `an ordered list numbers from its start`() {
        assertWrites(
            "3. three\n4. four",
            ListBlock(
                ordered = true,
                items = listOf(listOf(paragraph("three")), listOf(paragraph("four"))),
                start = 3,
                marker = ListMarker.PERIOD,
            ),
        )
    }

    @Test
    fun `a loose list separates its items with a blank line`() {
        // Looseness is what makes the document reparse the same way, so it has to be written back.
        assertWrites(
            "- one\n\n- two",
            ListBlock(
                ordered = false,
                items = listOf(listOf(paragraph("one")), listOf(paragraph("two"))),
                tight = false,
            ),
        )
    }

    @Test
    fun `a fenced code block writes its fence and language`() {
        assertWrites(
            "```kotlin\nval x = 1\n```",
            CodeBlock(text = "val x = 1", language = "kotlin", fence = CodeFence.DEFAULT),
        )
    }

    @Test
    fun `an indented code block writes four spaces and no fence`() {
        assertWrites("    indented", CodeBlock(text = "indented", fence = null))
    }

    @Test
    fun `a block quote prefixes every line`() {
        assertWrites("> quoted", BlockQuote(children = listOf(paragraph("quoted"))))
    }

    @Test
    fun `a thematic break writes three dashes`() {
        assertWrites("---", ThematicBreak())
    }

    @Test
    fun `link forms write back in the shape they were recorded`() {
        val text = listOf(Text("text"))

        assertWrites("[text](/url)", Paragraph(inlines = listOf(Link("/url", text))))
        assertWrites(
            "[text][ref]",
            Paragraph(inlines = listOf(Link("/url", text, form = LinkForm.Reference("ref")))),
        )
        assertWrites(
            "[ref]",
            Paragraph(inlines = listOf(Link("/url", text, form = LinkForm.Shortcut("ref")))),
        )
        assertWrites(
            "<https://example.com>",
            Paragraph(
                inlines =
                    listOf(
                        Link(
                            "https://example.com",
                            text,
                            form = LinkForm.Autolink(linkified = false),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `an image writes alt src and title`() {
        assertWrites(
            """![Alt](/img.png "Caption")""",
            Paragraph(inlines = listOf(Image(src = "/img.png", alt = "Alt", title = "Caption"))),
        )
    }

    @Test
    fun `blocks are separated by a blank line`() {
        val document =
            Document(blocks = listOf(Heading(level = 1, inlines = listOf(Text("T"))), paragraph("Body")))

        assertEquals("# T\n\nBody", serialiser.serialise(document))
    }

    private fun paragraph(text: String) = Paragraph(inlines = listOf(Text(text)))

    private fun assertWrites(
        expected: String,
        block: com.appthere.drafts.core.model.Block,
    ) {
        assertEquals(expected, serialiser.serialise(Document(blocks = listOf(block))))
    }
}
