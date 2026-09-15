package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Align
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.LinkForm
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
    fun `single tilde strikethrough is not recognised -- known divergence from the dialect`() {
        // markdown-dialect.md 2: "Single and double tilde both produce strikethrough." This does
        // not hold with intellij-markdown 0.7.13, and the failure is inconsistent rather than
        // absent:
        //
        //   ~single~        -> not strikethrough
        //   a ~single~ b    -> not strikethrough
        //   ~~a~~ and ~b~   -> BOTH are strikethrough
        //
        // So a single-tilde run is only recognised once a double-tilde run has appeared earlier in
        // the same paragraph. GFM itself specifies only `~~`, so the library is arguably right and
        // the dialect is asking for more -- but that third case is inconsistent under any reading.
        //
        // This test asserts what actually happens, so the divergence is visible rather than
        // forgotten. It will fail if upstream changes, which is the point: that is a signal to
        // revisit, not a regression. See the Phase 1 report -- this needs a decision, either to
        // amend the spec or to write a delimiter parser alongside the other custom extensions.
        val alone = parser.parse("~single~").blocks.flatMap { it.inlinesOf() }

        assertTrue(
            alone.filterIsInstance<Strikethrough>().isEmpty(),
            "Upstream now recognises a lone single-tilde run. Revisit the dialect divergence.",
        )
    }

    @Test
    fun `single tilde is recognised after a double tilde in the same paragraph`() {
        // The inconsistent half of the divergence above, pinned so it cannot change unnoticed.
        val struck =
            parser
                .parse("~~a~~ and ~b~")
                .blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<Strikethrough>()

        assertEquals(listOf(2, 1), struck.map { it.tildeCount })
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
