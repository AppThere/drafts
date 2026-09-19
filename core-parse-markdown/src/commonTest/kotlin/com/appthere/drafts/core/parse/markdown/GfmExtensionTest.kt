package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Align
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.LinkForm
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three extensions the dialect inherits from GFM: tables, strikethrough and linkify
 * (`markdown-dialect.md` 1-3).
 */
class GfmExtensionTest {
    private val parser = MarkdownDocumentParser()

    // --- tables ----------------------------------------------------------------------------------

    @Test
    fun `a table lowers its header and rows`() {
        val table = parser.parse("| A | B |\n|---|---|\n| 1 | 2 |\n").blocks.single() as Table

        assertEquals(2, table.header.size)
        assertEquals("A", table.header[0].plainText())
        assertEquals("B", table.header[1].plainText())
        assertEquals(1, table.rows.size)
        assertEquals(listOf("1", "2"), table.rows.single().map { it.plainText() })
    }

    @Test
    fun `column alignment comes from the delimiter row colons`() {
        val table =
            parser
                .parse("| L | R | C | N |\n|:--|--:|:-:|---|\n| 1 | 2 | 3 | 4 |\n")
                .blocks
                .single() as Table

        assertEquals(listOf(Align.LEFT, Align.RIGHT, Align.CENTER, Align.NONE), table.alignments)
    }

    @Test
    fun `table cells carry inline content`() {
        // markdown-dialect.md 1: "Cells contain inline content only; no block content."
        val table = parser.parse("| *a* | `b` |\n|---|---|\n| c | d |\n").blocks.single() as Table

        assertEquals("a", table.header[0].plainText())
        assertEquals("b", table.header[1].plainText())
    }

    @Test
    fun `alignments always match the header width`() {
        // Table requires the two to agree. An editor sees half-typed tables constantly, and an IR
        // invariant that throws would surface as a crash mid-keystroke rather than as a table that
        // is not finished yet.
        val document = parser.parse("| A | B | C |\n|---|---|\n| 1 | 2 | 3 |\n")
        val tables = document.blocks.filterIsInstance<Table>()

        tables.forEach { assertEquals(it.header.size, it.alignments.size) }
    }

    @Test
    fun `multiple body rows are kept in order`() {
        val table = parser.parse("| A |\n|---|\n| 1 |\n| 2 |\n| 3 |\n").blocks.single() as Table

        assertEquals(listOf("1", "2", "3"), table.rows.map { it.single().plainText() })
    }

    // --- strikethrough ---------------------------------------------------------------------------

    @Test
    fun `double tilde strikethrough records two tildes`() {
        val struck = parser.parse("~~struck~~").firstInline<Strikethrough>()

        assertEquals(2, struck.tildeCount)
        assertEquals("struck", struck.children.plainText())
    }

    @Test
    fun `single tilde strikethrough is recognised`() {
        // gfm.md 3: "One or two tildes: `~text~` or `~~text~~`." markdown-dialect.md 2 agrees, and
        // so does Hugo via Goldmark. intellij-markdown is the one that does not -- it produces a
        // single-tilde run only after a double-tilde run has appeared in the same paragraph -- so
        // this dialect finds them itself.
        val struck = parser.parse("~single~").firstInline<Strikethrough>()

        assertEquals(1, struck.tildeCount)
        assertEquals("single", struck.children.plainText())
    }

    @Test
    fun `single tilde works mid-sentence`() {
        val struck = parser.parse("a ~single~ b").firstInline<Strikethrough>()

        assertEquals("single", struck.children.plainText())
    }

    @Test
    fun `text either side of a single tilde run survives`() {
        val paragraph = parser.parse("before ~struck~ after").blocks.single() as Paragraph

        val rendered =
            paragraph.inlines.joinToString("") { inline ->
                if (inline is Strikethrough) "<del>" else listOf(inline).plainText()
            }

        assertEquals("before <del> after", rendered)
    }

    @Test
    fun `a tilde with spaces inside is not strikethrough`() {
        // Approximated flanking: an opening tilde must be followed by a non-space and a closing one
        // preceded by a non-space.
        val struck =
            parser
                .parse("~ spaced ~")
                .blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<Strikethrough>()

        assertTrue(struck.isEmpty())
    }

    @Test
    fun `three or more tildes are not strikethrough`() {
        // markdown-dialect.md 2 states this directly, and here the library agrees.
        val struck =
            parser
                .parse("~~~triple~~~")
                .blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<Strikethrough>()

        assertTrue(struck.isEmpty())
    }

    @Test
    fun `tilde markers are filtered out of the content`() {
        assertEquals(
            "x",
            parser
                .parse("~~x~~")
                .firstInline<Strikethrough>()
                .children
                .plainText(),
        )
    }

    // --- linkify ---------------------------------------------------------------------------------

    @Test
    fun `a bare www host gets https not http`() {
        // The dialect's one deliberate divergence from GFM (markdown-dialect.md 3), following
        // Goldmark's linkifyProtocol default.
        val link = parser.parse("Visit www.example.com today").firstInline<Link>()

        assertEquals("https://www.example.com", link.href)
        assertEquals(LinkForm.Autolink(linkified = true), link.form)
    }

    @Test
    fun `a bare url with a scheme keeps its own scheme`() {
        assertEquals("http://x.test", parser.parse("See http://x.test now").firstInline<Link>().href)
        assertEquals("https://y.test", parser.parse("See https://y.test now").firstInline<Link>().href)
    }

    @Test
    fun `a linkified autolink is distinguishable from an explicit one`() {
        // The two serialise differently -- `<...>` versus bare text -- so the IR has to tell them
        // apart or one of them comes back wrong.
        val explicit = parser.parse("<https://x.test>").firstInline<Link>()
        val linkified = parser.parse("bare https://x.test here").firstInline<Link>()

        assertEquals(LinkForm.Autolink(linkified = false), explicit.form)
        assertEquals(LinkForm.Autolink(linkified = true), linkified.form)
    }

    @Test
    fun `the linkified text is the text the author typed`() {
        val link = parser.parse("Visit www.example.com today").firstInline<Link>()

        assertTrue(
            link.children.plainText() == "www.example.com",
            "The displayed text keeps the scheme the author omitted: ${link.children.plainText()}",
        )
    }
}
