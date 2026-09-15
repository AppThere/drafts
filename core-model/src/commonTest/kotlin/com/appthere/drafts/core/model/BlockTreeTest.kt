package com.appthere.drafts.core.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tree shape and the invariants the specs state.
 *
 * The invariants are the valuable half. Each `require` here corresponds to a sentence in
 * `markdown-dialect.md` or `export-pipeline.md`, and each test names it -- a construct that cannot
 * exist in Markdown should not be constructible in the IR either, because the alternative is
 * discovering it in a backend that emits something no reader can open.
 */
class BlockTreeTest {
    @Test
    fun `a block quote exposes its children`() {
        val inner = paragraph("inner")
        val quote = BlockQuote(children = listOf(inner))

        assertContentEquals(listOf(inner), quote.children)
    }

    @Test
    fun `a list flattens its items into children`() {
        // Items are a list of lists -- each item is a sequence of blocks. Flattening is what lets
        // a walk treat a list like any other container instead of special-casing it.
        val first = paragraph("one")
        val second = paragraph("two")
        val third = paragraph("three")

        val list =
            ListBlock(
                ordered = false,
                items = listOf(listOf(first, second), listOf(third)),
            )

        assertContentEquals(listOf(first, second, third), list.children)
    }

    @Test
    fun `a definition list flattens its definitions into children`() {
        val body = paragraph("a definition")
        val definitions =
            DefinitionList(
                entries = listOf(DefEntry(term = listOf(Text("Term")), definitions = listOf(listOf(body)))),
            )

        assertContentEquals(listOf(body), definitions.children)
    }

    @Test
    fun `leaves have no children`() {
        assertTrue(paragraph("x").children.isEmpty())
        assertTrue(ThematicBreak().children.isEmpty())
        assertTrue(CodeBlock(text = "x").children.isEmpty())
    }

    @Test
    fun `a nested tree can be walked depth first`() {
        // The property that matters for every backend: containers nest arbitrarily and a single
        // uniform accessor reaches all of it.
        val document =
            listOf(
                Heading(level = 1, inlines = listOf(Text("Title"))),
                BlockQuote(
                    children =
                        listOf(
                            ListBlock(
                                ordered = true,
                                marker = ListMarker.PERIOD,
                                items = listOf(listOf(paragraph("deep"))),
                            ),
                        ),
                ),
            )

        val roles = document.flatMap { it.walk() }.map { it.role }

        assertEquals(
            listOf(BlockRole.HEADING, BlockRole.QUOTE, BlockRole.LIST_ITEM, BlockRole.BODY),
            roles,
        )
    }

    // --- invariants ------------------------------------------------------------------------------

    @Test
    fun `heading levels outside one to six are rejected`() {
        assertFailsWith<IllegalArgumentException> { Heading(level = 0, inlines = emptyList()) }
        assertFailsWith<IllegalArgumentException> { Heading(level = 7, inlines = emptyList()) }
    }

    @Test
    fun `a setext heading cannot exceed level two`() {
        // Setext underlining has exactly two spellings, `===` and `---`. There is no way to write
        // a level 3 Setext heading, so there should be no way to model one.
        assertFailsWith<IllegalArgumentException> {
            Heading(level = 3, inlines = emptyList(), style = HeadingStyle.SETEXT)
        }

        Heading(level = 2, inlines = emptyList(), style = HeadingStyle.SETEXT)
    }

    @Test
    fun `a list marker must agree with whether the list is ordered`() {
        assertFailsWith<IllegalArgumentException> {
            ListBlock(ordered = true, items = emptyList(), marker = ListMarker.DASH)
        }
        assertFailsWith<IllegalArgumentException> {
            ListBlock(ordered = false, items = emptyList(), marker = ListMarker.PERIOD)
        }
    }

    @Test
    fun `a table's alignments must match its header cell count`() {
        // markdown-dialect.md 1 and gfm.md both state it: "the delimiter row must have exactly
        // the same cell count as the header row, or the whole construct is not a table."
        assertFailsWith<IllegalArgumentException> {
            Table(
                header = listOf(listOf(Text("A")), listOf(Text("B"))),
                alignments = listOf(Align.LEFT),
                rows = emptyList(),
            )
        }
    }

    @Test
    fun `a code fence is backticks or tildes and at least three of them`() {
        assertFailsWith<IllegalArgumentException> { CodeFence('-', 3) }
        assertFailsWith<IllegalArgumentException> { CodeFence('`', 2) }

        assertEquals(3, CodeFence.DEFAULT.length)
    }

    @Test
    fun `an indented code block has no fence`() {
        // The distinction is semantic, not cosmetic: an indented block re-emitted as a fenced one
        // changes which characters are significant inside it.
        assertEquals(null, CodeBlock(text = "indented").fence)
    }

    // --- round-trip fidelity ---------------------------------------------------------------------

    @Test
    fun `emphasis delimiter is part of identity`() {
        // If these compared equal, a serialiser could swap one for the other without any test
        // noticing, and round-trip contract item 3 would be unenforceable.
        val asterisk = Emphasis(strong = false, children = listOf(Text("x")))
        val underscore =
            Emphasis(
                strong = false,
                children = listOf(Text("x")),
                delimiter = EmphasisDelimiter.UNDERSCORE,
            )

        assertTrue(asterisk != underscore)
    }

    @Test
    fun `strikethrough rejects three or more tildes`() {
        // markdown-dialect.md 2: one or two tildes strike through, three or more do not. A
        // three-tilde run is literal text and never reaches this type.
        assertFailsWith<IllegalArgumentException> {
            Strikethrough(children = listOf(Text("x")), tildeCount = 3)
        }
    }

    @Test
    fun `a code span needs at least one backtick`() {
        assertFailsWith<IllegalArgumentException> { CodeSpan(text = "x", backtickCount = 0) }
    }

    private fun paragraph(text: String) = Paragraph(inlines = listOf(Text(text)))
}

/** Depth-first walk over a block and everything under it, the block itself first. */
private fun Block.walk(): List<Block> = listOf(this) + children.flatMap { it.walk() }
