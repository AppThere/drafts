package com.appthere.drafts.a11y

import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.ListMarker
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.ThematicBreak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 10.1: what each block is called, and which changes to a block are worth saying out loud. */
class BlockNamesTest {
    @Test
    fun `blocks are named as a writer names them`() {
        assertEquals("Block quote", BlockNames.kindOf(BlockQuote(listOf(PARAGRAPH))))
        assertEquals(
            "Numbered list",
            BlockNames.kindOf(ListBlock(ordered = true, items = emptyList(), marker = ListMarker.PERIOD)),
        )
        assertEquals("Bulleted list", BlockNames.kindOf(ListBlock(ordered = false, items = emptyList())))
        assertEquals("Section break", BlockNames.kindOf(ThematicBreak()))
        assertEquals("Heading level 2", BlockNames.kindOf(Heading(level = 2, inlines = emptyList())))
    }

    @Test
    fun `a code block is named with its language as it is spoken`() {
        // 10.1's own example: "Code block, Kotlin".
        assertEquals("Code block, Kotlin", BlockNames.kindOf(CodeBlock(text = "", language = "kotlin")))
        assertEquals("Code block", BlockNames.kindOf(CodeBlock(text = "", language = null)))
        assertEquals("Code block", BlockNames.kindOf(CodeBlock(text = "", language = "  ")))
    }

    @Test
    fun `a paragraph and a heading take no prefix`() {
        // A paragraph is the default reading, and a heading says what it is through heading().
        assertNull(BlockNames.prefixOf(PARAGRAPH))
        assertNull(BlockNames.prefixOf(Heading(level = 1, inlines = emptyList())))
        assertEquals("Block quote", BlockNames.prefixOf(BlockQuote(listOf(PARAGRAPH))))
    }

    @Test
    fun `a paragraph promoted to a heading is announced`() {
        // 10.1's example, word for word.
        assertEquals("Heading level 2.", BlockNames.announcement(PARAGRAPH, Heading(level = 2, inlines = emptyList())))
    }

    @Test
    fun `a heading whose level changes is announced`() {
        assertEquals(
            "Heading level 3.",
            BlockNames.announcement(
                Heading(level = 2, inlines = emptyList()),
                Heading(level = 3, inlines = emptyList()),
            ),
        )
    }

    @Test
    fun `typing that leaves a block the kind it was is not announced`() {
        // Every keystroke changes the block. Saying so each time is the chattiness 10.1 warns of.
        assertNull(BlockNames.announcement(PARAGRAPH, Paragraph(listOf(Text("Words, and more words.")))))
    }

    private companion object {
        val PARAGRAPH = Paragraph(listOf(Text("Words.")))
    }
}
