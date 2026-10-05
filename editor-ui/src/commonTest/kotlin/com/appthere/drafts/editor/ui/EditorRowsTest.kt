package com.appthere.drafts.editor.ui

import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fountain's dual dialogue found in a screenplay's blocks, and the list's rows over them: a pair
 * set side by side is one row, so everything that speaks in blocks -- the caret, the outline, a
 * restored scroll position -- has to be translated to reach it.
 */
class EditorRowsTest {
    @Test
    fun `a marked speech pairs with the speech before it`() {
        val pairs = dualPairsOf(screenplay(DUAL).blocks)

        // Action, BRICK, his line, STEEL, his aside, his line, action.
        assertEquals(listOf(DualPair(first = 1..2, second = 3..5)), pairs)
    }

    @Test
    fun `a marked speech with no speech before it pairs with nothing`() {
        val pairs = dualPairsOf(screenplay("$ACTION\n\nSTEEL ^\nScrew retirement.\n").blocks)

        assertTrue(pairs.isEmpty(), "Paired with $pairs")
    }

    @Test
    fun `a pair is one row and the blocks around it keep theirs`() {
        val rows = EditorRows(blockCount = 7, folded = listOf(DualPair(1..2, 3..5)))

        assertEquals(3, rows.size)
        assertEquals(listOf(0, 1, 6), (0 until rows.size).map(rows::blockAt))
        assertEquals(listOf(0, 1, 1, 1, 1, 1, 2), (0 until 7).map(rows::rowOf))
        assertEquals(DualPair(1..2, 3..5), rows.pairAt(1))
        assertNull(rows.pairAt(0))
        assertNull(rows.pairAt(2))
    }

    @Test
    fun `rows and blocks translate both ways past several pairs`() {
        val rows = EditorRows(blockCount = 12, folded = listOf(DualPair(1..2, 3..4), DualPair(6..7, 8..10)))

        (0 until rows.size).forEach { row -> assertEquals(row, rows.rowOf(rows.blockAt(row)), "row $row") }
        assertEquals(listOf(0, 1, 5, 6, 11), (0 until rows.size).map(rows::blockAt))
    }

    @Test
    fun `without a pair a row is a block`() {
        val rows = EditorRows(blockCount = 4, folded = emptyList())

        assertEquals(4, rows.size)
        assertEquals(listOf(0, 1, 2, 3), (0 until 4).map(rows::blockAt))
        assertEquals(listOf(0, 1, 2, 3), (0 until 4).map(rows::rowOf))
    }

    @Test
    fun `the pair with the caret in it is not folded`() {
        val state = EditorState(screenplay(DUAL))
        assertEquals(3, state.rows.size)

        state.place(Caret(state.blocks[4].id, 0))
        assertEquals(7, state.rows.size, "The pair being edited was folded into one row")

        state.place(Caret(state.blocks.last().id, 0))
        assertEquals(3, state.rows.size, "The pair did not fold again when the caret left it")
    }

    @Test
    fun `a Markdown document has no pairs whatever it says`() {
        val state = EditorState(DocumentSession("BRICK\nScrew retirement.\n\nSTEEL ^\nScrew retirement.\n"))

        assertTrue(state.dualPairs.isEmpty())
        assertEquals(state.blocks.size, state.rows.size)
    }

    @Test
    fun `only the second name is said to be simultaneous`() {
        val blocks = screenplay(DUAL).blocks

        assertEquals(listOf(3), blocks.indices.filter { isSimultaneous(blocks[it].block) })
    }

    private fun screenplay(text: String) = DocumentSession(text, BlockParser.Fountain())

    private companion object {
        const val ACTION = "They face each other."
        const val BRICK = "BRICK\nScrew retirement."
        const val STEEL = "STEEL ^\n(grinning)\nScrew retirement."
        const val DUAL = "$ACTION\n\n$BRICK\n\n$STEEL\n\nThey laugh.\n"
    }
}
