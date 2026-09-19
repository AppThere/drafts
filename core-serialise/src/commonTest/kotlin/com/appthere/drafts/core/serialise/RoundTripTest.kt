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
    fun `a table round-trips byte for byte`() {
        assertRoundTrips("| A | B |\n|:--|--:|\n| 1 | 2 |\n")
    }

    @Test
    fun `a table with irregular cell padding is not tidied up`() {
        // A renderer would happily align these columns. An editor must not: the author's spacing is
        // their file, and re-aligning it turns one edited cell into a whole-table diff.
        assertRoundTrips("|A|Long header|\n|---|---|\n|1|   padded   |\n")
    }

    @Test
    fun `strikethrough tilde counts are preserved`() {
        assertRoundTrips("Some ~~double~~ and some ~single~ strikethrough.\n")
    }

    @Test
    fun `a linkified bare url is not rewritten as an explicit autolink`() {
        // The https default applies to the href, not to the text. Writing back `<https://...>`
        // would put a scheme on screen that the author never typed.
        assertRoundTrips("Visit www.example.com and https://x.test today.\n")
    }

    @Test
    fun `YAML front matter round-trips verbatim`() {
        // The serialiser needs no knowledge of front matter: it sits before the first block's span
        // and is copied as part of the leading gap. This test is what proves that reasoning.
        assertRoundTrips("---\ntitle: Test\nauthor: Someone\n---\n\n# Heading\n")
    }

    @Test
    fun `TOML front matter keeps its key order and spacing`() {
        // markdown-dialect.md: preserved "including whitespace and key order", never normalised.
        assertRoundTrips("+++\nzebra  =  1\nalpha=2\n\ngamma = 3\n+++\n\nBody.\n")
    }

    @Test
    fun `JSON front matter round-trips`() {
        assertRoundTrips("{\n  \"title\": \"Test\"\n}\n\nBody.\n")
    }

    @Test
    fun `shortcodes round-trip verbatim`() {
        assertRoundTrips("{{< figure src=\"a.png\" >}}\n\nBody text.\n")
    }

    @Test
    fun `an inline shortcode keeps the text either side of it`() {
        assertRoundTrips("Text before {{% notice %}} and after.\n")
    }

    @Test
    fun `a shortcode inside a code fence is not touched`() {
        assertRoundTrips("```\n{{< figure src=\"a.png\" >}}\n```\n")
    }

    @Test
    fun `front matter and shortcodes together round-trip`() {
        assertRoundTrips(
            "---\ntitle: Test\n---\n\n{{< figure src=\"a.png\" >}}\n\nProse with {{% x %}} inline.\n",
        )
    }

    @Test
    fun `footnotes round-trip even though definitions leave the block list`() {
        // The definitions are lifted into Document.footnotes, so their source region becomes a gap
        // between blocks -- and gaps are copied verbatim. This test is what proves that removing
        // them costs nothing on the way back out.
        assertRoundTrips("Text with a ref.[^1]\n\n[^1]: The footnote body.\n")
    }

    @Test
    fun `an unreferenced footnote definition still round-trips`() {
        assertRoundTrips("Just prose.\n\n[^unused]: Nobody points here.\n")
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
