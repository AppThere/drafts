package com.appthere.drafts.editor.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Selection across blocks, which `appthere-drafts.md` 4.4 calls the largest unknown in the project.
 *
 * Two of the Phase 2 gate criteria are decided here: "Select-all -> copy yields correct source
 * text", and the correctness half of "Selection drag across 50+ blocks is smooth and the highlight
 * is correct". Smoothness is a UI measurement; correctness is this.
 */
class SelectionTest {
    @Test
    fun `a selection inside one block yields that text`() {
        val session = DocumentSession(THREE)
        val block = session.blocks[1].id

        val selection = Selection(Caret(block, 0), Caret(block, "Second".length))

        assertEquals("Second", session.textIn(selection))
    }

    @Test
    fun `a selection across blocks yields the raw source between them`() {
        // 4.4: "Copy of a multi-block selection yields the raw source of those blocks." The blank
        // line between the blocks is part of that -- it is between the two offsets.
        val session = DocumentSession(THREE)

        val selection =
            Selection(
                anchor = Caret(session.blocks[0].id, "First para.".length),
                focus = Caret(session.blocks[1].id, "Second".length),
            )

        assertEquals("\n\nSecond", session.textIn(selection))
    }

    @Test
    fun `a selection dragged backwards yields the same text as forwards`() {
        // The user can drag either way. Anchor and focus record which end moved; document order is
        // worked out on use, so a backwards drag is not a special case anywhere else.
        val session = DocumentSession(THREE)
        val forwards =
            Selection(Caret(session.blocks[0].id, 0), Caret(session.blocks[1].id, "Second".length))
        val backwards = Selection(forwards.focus, forwards.anchor)

        assertEquals(session.textIn(forwards), session.textIn(backwards))
    }

    @Test
    fun `select all then copy yields the whole document`() {
        // A gate criterion, worded exactly this way in the plan.
        val session = DocumentSession(THREE)

        val all = session.selectAll()!!

        assertEquals(session.text.trimEnd('\n'), session.textIn(all))
    }

    @Test
    fun `select all across a document of ten thousand words yields all of it`() {
        val session = DocumentSession(GateFixture.tenThousandWords())

        val copied = session.textIn(session.selectAll()!!)

        assertEquals(session.text.trimEnd('\n'), copied)
        assertTrue(copied.length > MIN_FIXTURE_LENGTH, "Only ${copied.length} code units copied")
    }

    @Test
    fun `an empty selection yields nothing`() {
        val session = DocumentSession(THREE)
        val caret = Caret(session.blocks[1].id, 3)

        assertEquals("", session.textIn(Selection.at(caret)))
        assertTrue(Selection.at(caret).isCollapsed)
    }

    @Test
    fun `deleting a selection removes exactly what it covered`() {
        val session = DocumentSession(THREE)
        val selection =
            Selection(
                anchor = Caret(session.blocks[0].id, "First ".length),
                focus = Caret(session.blocks[1].id, "Second ".length),
            )

        session.delete(selection, UndoHistory())

        assertEquals("First para.\n\nThird para.\n", session.text)
    }

    @Test
    fun `deleting across blocks leaves the caret at the join`() {
        val session = DocumentSession(THREE)
        val selection =
            Selection(
                anchor = Caret(session.blocks[0].id, "First".length),
                focus = Caret(session.blocks[1].id, "Second".length),
            )

        val caret = session.delete(selection, UndoHistory())!!

        assertEquals(session.blocks[0].id, caret.block)
        assertEquals("First".length, caret.offset)
    }

    @Test
    fun `a selection whose block has gone yields nothing rather than the wrong thing`() {
        val session = DocumentSession(THREE)
        val stale = Selection(Caret(BlockId(9999), 0), Caret(BlockId(9999), 5))

        assertNull(session.spanOf(stale))
        assertEquals("", session.textIn(stale))
    }

    private companion object {
        const val THREE = "First para.\n\nSecond para.\n\nThird para.\n"
        const val MIN_FIXTURE_LENGTH = 50_000
    }
}
