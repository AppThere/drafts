package com.appthere.drafts.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a reader would read, from inlines with their markup flattened away. */
class PlainTextTest {
    @Test
    fun `markup is flattened to the words inside it`() {
        val inlines =
            listOf(
                Text("The "),
                Emphasis(strong = true, children = listOf(Text("salt"))),
                Text(" "),
                Link(href = "https://example.com", children = listOf(Strikethrough(listOf(Text("road"))))),
            )

        assertEquals("The salt road", inlines.plainText())
    }

    @Test
    fun `code keeps its words`() {
        assertEquals("Using foo", listOf(Text("Using "), CodeSpan("foo")).plainText())
    }

    @Test
    fun `things that are not words contribute none`() {
        // An image's alt text describes a picture, a footnote marker is a pointer, and raw HTML or a
        // shortcode is instructions to a renderer. None of it is read as part of the sentence.
        val inlines =
            listOf(
                Text("Chapter"),
                Image(src = "map.png", alt = "a map"),
                FootnoteRef("1"),
                RawInline("{{< aside >}}", Origin.HUGO_SHORTCODE_ANGLE),
            )

        assertEquals("Chapter", inlines.plainText())
    }

    @Test
    fun `a line break reads as a space`() {
        assertEquals("Part one", listOf(Text("Part"), LineBreak(hard = true), Text("one")).plainText())
    }
}
