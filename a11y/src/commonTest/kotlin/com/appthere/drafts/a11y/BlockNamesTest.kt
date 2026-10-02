package com.appthere.drafts.a11y

import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.ListMarker
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.ThematicBreak
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.block_bulleted_list
import com.appthere.drafts.i18n.resources.block_code
import com.appthere.drafts.i18n.resources.block_numbered_list
import com.appthere.drafts.i18n.resources.block_quote
import com.appthere.drafts.i18n.resources.block_section_break
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 10.1: what each block is called, and which changes to a block are worth saying out loud.
 *
 * Asserted on the *name* rather than on the words, which is what 11.1 made of this: the words are
 * in `values/strings.xml` and reading one needs a composition. What is decided here is which of
 * them a block gets, and a test that asserted the English would start failing the day somebody
 * translated the file -- which is the opposite of what it should do.
 */
class BlockNamesTest {
    @Test
    fun `blocks are named as a writer names them`() {
        assertEquals(BlockName.Named(Res.string.block_quote), BlockNames.kindOf(BlockQuote(listOf(PARAGRAPH))))
        assertEquals(
            BlockName.Named(Res.string.block_numbered_list),
            BlockNames.kindOf(ListBlock(ordered = true, items = emptyList(), marker = ListMarker.PERIOD)),
        )
        assertEquals(
            BlockName.Named(Res.string.block_bulleted_list),
            BlockNames.kindOf(ListBlock(ordered = false, items = emptyList())),
        )
        assertEquals(BlockName.Named(Res.string.block_section_break), BlockNames.kindOf(ThematicBreak()))
        assertEquals(BlockName.HeadingAt(2), BlockNames.kindOf(Heading(level = 2, inlines = emptyList())))
    }

    @Test
    fun `a code block is named with its language as it is spoken`() {
        // 10.1's own example: "Code block, Kotlin". The capital is this application's doing, so it
        // is asserted here; the comma and the word "Code block" are the resource's.
        assertEquals(BlockName.CodeIn("Kotlin"), BlockNames.kindOf(CodeBlock(text = "", language = "kotlin")))
        assertEquals(BlockName.Named(Res.string.block_code), BlockNames.kindOf(CodeBlock(text = "", language = null)))
        assertEquals(BlockName.Named(Res.string.block_code), BlockNames.kindOf(CodeBlock(text = "", language = "  ")))
    }

    @Test
    fun `a paragraph and a heading take no prefix`() {
        // A paragraph is the default reading, and a heading says what it is through heading().
        assertNull(BlockNames.prefixOf(PARAGRAPH))
        assertNull(BlockNames.prefixOf(Heading(level = 1, inlines = emptyList())))
        assertEquals(BlockName.Named(Res.string.block_quote), BlockNames.prefixOf(BlockQuote(listOf(PARAGRAPH))))
    }

    @Test
    fun `a paragraph promoted to a heading is announced`() {
        // 10.1's example. The full stop that makes it a sentence is a resource of its own.
        assertEquals(BlockName.HeadingAt(2), BlockNames.changed(PARAGRAPH, Heading(level = 2, inlines = emptyList())))
    }

    @Test
    fun `a heading whose level changes is announced`() {
        assertEquals(
            BlockName.HeadingAt(3),
            BlockNames.changed(
                Heading(level = 2, inlines = emptyList()),
                Heading(level = 3, inlines = emptyList()),
            ),
        )
    }

    @Test
    fun `typing that leaves a block the kind it was is not announced`() {
        // Every keystroke changes the block. Saying so each time is the chattiness 10.1 warns of.
        assertNull(BlockNames.changed(PARAGRAPH, Paragraph(listOf(Text("Words, and more words.")))))
    }

    private companion object {
        val PARAGRAPH = Paragraph(listOf(Text("Words.")))
    }
}
