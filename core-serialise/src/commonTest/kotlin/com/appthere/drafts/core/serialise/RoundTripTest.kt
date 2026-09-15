package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.parse.markdown.MarkdownDocumentParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parse, serialise, compare bytes.
 *
 * Phase 1's acceptance: "round-trip is byte-identical across a corpus of real-world documents".
 * This is the property the editor's entire safety story rests on -- a save must not rewrite text
 * the user never touched, and it must not do so *invisibly*, which is how this class of bug
 * normally ships.
 *
 * The corpus deliberately includes spellings a renderer would happily normalise away: underscore
 * emphasis, `*` and `+` bullets, Setext headings, four-space indented code, tabs, and irregular
 * blank-line runs. Each one is a chance to silently "improve" someone's file.
 */
class RoundTripTest {
    private val parser = MarkdownDocumentParser()
    private val serialiser = MarkdownSerialiser()

    @Test
    fun `a simple document round-trips byte for byte`() {
        assertRoundTrips("# Title\n\nA paragraph.\n")
    }

    @Test
    fun `underscore emphasis is not rewritten as asterisks`() {
        // Round-trip contract item 3, the one Phase 1's acceptance names.
        assertRoundTrips("Some _emphasis_ and some __strong__ text.\n")
    }

    @Test
    fun `mixed emphasis delimiters in one document both survive`() {
        assertRoundTrips("A *star* and an _underscore_ in the same paragraph.\n")
    }

    @Test
    fun `bullet characters are preserved`() {
        assertRoundTrips("* star item\n* another\n")
        assertRoundTrips("+ plus item\n+ another\n")
        assertRoundTrips("- dash item\n- another\n")
    }

    @Test
    fun `a setext heading is not converted to ATX`() {
        assertRoundTrips("Title\n=====\n\nSubtitle\n--------\n\nBody.\n")
    }

    @Test
    fun `an indented code block is not converted to a fence`() {
        assertRoundTrips("Text.\n\n    indented code\n    second line\n\nMore text.\n")
    }

    @Test
    fun `a fenced code block keeps its fence length and language`() {
        assertRoundTrips("````kotlin\nval x = 1\n````\n")
    }

    @Test
    fun `irregular blank line runs are preserved`() {
        // Whitespace between blocks lives in the gaps, not in any span. If the serialiser rebuilt
        // separators instead of copying them, this is where it would show.
        assertRoundTrips("One.\n\n\n\nTwo.\n\n\nThree.\n")
    }

    @Test
    fun `a document with no trailing newline stays that way`() {
        assertRoundTrips("No trailing newline")
    }

    @Test
    fun `reference links and their definitions are preserved`() {
        assertRoundTrips("See [the docs][ref] for more.\n\n[ref]: https://example.com \"Docs\"\n")
    }

    @Test
    fun `nested structure round-trips`() {
        assertRoundTrips(
            """
            > A quote with a list:
            >
            > - one
            > - two
            >
            > And a closing line.
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun `a longer mixed document round-trips`() {
        assertRoundTrips(
            """
            Title
            =====

            An opening paragraph with _emphasis_, **strong**, `code`, and a
            [link](https://example.com "Title").

            ## A section

            * a star bullet
            * with a second item

            1) a paren-delimited ordered list
            2) second

                indented code inside the flow

            ```
            fenced code, no language
            ```

            > quoted text
            > across two lines

            ---

            A closing paragraph with an <https://example.com> autolink.
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun `non-ASCII content round-trips`() {
        // Offsets are UTF-16 code units; an astral-plane character is two of them. A span that
        // counted wrong would slice mid-surrogate and corrupt the text here.
        assertRoundTrips("Café — naïve. 💡 An emoji, and 日本語.\n\nSecond *paragraph*.\n")
    }

    private fun assertRoundTrips(source: String) {
        val output = serialiser.serialise(parser.parse(source), source)

        assertEquals(source, output, "Round-trip changed the document")
    }
}
