package com.appthere.drafts.editor.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Caret movement across block boundaries, and the two edits that change block structure.
 *
 * `IMPLEMENTATION-PLAN.md` Phase 2: "Caret movement across block boundaries; Enter splits;
 * Backspace at offset 0 merges; structural reparse preserves focus and caret offset." The last
 * clause is the one worth testing hardest -- moving the caret is easy, and keeping it in the right
 * place *after the block list has been rebuilt underneath it* is where this architecture is most
 * likely to fail.
 */
class CaretTest {
    @Test
    fun `the caret leaves a block upwards into the end of the one above`() {
        val session = DocumentSession(THREE)
        val second = session.blocks[1].id

        val moved = session.caretBefore(second)

        assertEquals(Caret(session.blocks[0].id, "First para.".length), moved)
    }

    @Test
    fun `the caret leaves a block downwards into the start of the one below`() {
        val session = DocumentSession(THREE)
        val second = session.blocks[1].id

        val moved = session.caretAfter(second)

        assertEquals(Caret(session.blocks[2].id, 0), moved)
    }

    @Test
    fun `there is nothing above the first block or below the last`() {
        val session = DocumentSession(THREE)

        assertNull(session.caretBefore(session.blocks.first().id))
        assertNull(session.caretAfter(session.blocks.last().id))
    }

    @Test
    fun `a caret converts to a document offset and back`() {
        val session = DocumentSession(THREE)
        val caret = Caret(session.blocks[1].id, "Second".length)

        val offset = session.offsetIn(caret)!!

        assertEquals("Second", session.text.substring(offset - "Second".length, offset))
        assertEquals(caret, session.caretAt(offset))
    }

    @Test
    fun `an offset in the blank line between blocks belongs to the block above`() {
        // Not a corner case for its own sake: a merge deletes the separator, and the offset it
        // hands back is momentarily one of these.
        val session = DocumentSession(THREE)
        val firstEnd =
            session.blocks[0]
                .block.source!!
                .endExclusive.value

        val caret = session.caretAt(firstEnd + 1)

        assertEquals(Caret(session.blocks[0].id, "First para.".length), caret)
    }

    @Test
    fun `Enter splits a paragraph in two`() {
        val session = DocumentSession("One two.\n")
        val caret = Caret(session.blocks[0].id, "One ".length)

        session.split(caret, UndoHistory())

        assertEquals("One \n\ntwo.\n", session.text)
        assertEquals(2, session.blocks.size)
    }

    @Test
    fun `the caret lands at the head of the second half after a split`() {
        // The criterion is "structural reparse preserves focus and caret offset". After Enter the
        // user expects to be typing at the start of the new block, not wherever the old block's id
        // ended up.
        val session = DocumentSession("One two.\n")
        val caret = Caret(session.blocks[0].id, "One ".length)

        val moved = session.split(caret, UndoHistory())!!

        assertEquals(session.blocks[1].id, moved.block)
        assertEquals(0, moved.offset)
        assertEquals("two.", session.sourceOf(session.blocks[1].block))
    }

    @Test
    fun `splitting a heading leaves a heading and a paragraph`() {
        val session = DocumentSession("## Head ing\n")
        val caret = Caret(session.blocks[0].id, "## Head".length)

        session.split(caret, UndoHistory())

        assertEquals("## Head\n\n ing\n", session.text)
    }

    @Test
    fun `Backspace at offset zero merges a paragraph into the one above`() {
        val session = DocumentSession("First.\n\nSecond.\n")
        val caret = Caret(session.blocks[1].id, 0)

        session.mergeWithPrevious(caret, UndoHistory())

        assertEquals("First.Second.\n", session.text)
        assertEquals(1, session.blocks.size)
    }

    @Test
    fun `the caret lands at the join after a merge`() {
        val session = DocumentSession("First.\n\nSecond.\n")
        val caret = Caret(session.blocks[1].id, 0)

        val moved = session.mergeWithPrevious(caret, UndoHistory())!!

        assertEquals(session.blocks[0].id, moved.block)
        assertEquals("First.".length, moved.offset)
    }

    @Test
    fun `merging removes however many blank lines the author left`() {
        // One blank line and a stray line break: not enough for an empty paragraph (`roomsIn`), so
        // the gap is all separator and one Backspace takes the whole of it.
        val session = DocumentSession("First.\n\n\nSecond.\n")
        val caret = Caret(session.blocks[1].id, 0)

        session.mergeWithPrevious(caret, UndoHistory())

        assertEquals("First.Second.\n", session.text)
    }

    @Test
    fun `backspace takes an empty paragraph first and merges after`() {
        // Two blank lines are an empty paragraph between the two, shown as one. Backspace at the
        // start of the second removes the empty one, as it would in any editor, and only the next
        // Backspace joins the paragraphs.
        val session = DocumentSession("First.\n\n\n\nSecond.\n")
        val history = UndoHistory()

        val afterFirst = session.mergeWithPrevious(Caret(session.blocks.last().id, 0), history)
        assertEquals("First.\n\nSecond.\n", session.text)

        session.mergeWithPrevious(Caret(session.blocks.last().id, afterFirst?.offset ?: 0), history)
        assertEquals("First.Second.\n", session.text)
    }

    @Test
    fun `there is nothing to merge the first block into`() {
        val session = DocumentSession("First.\n\nSecond.\n")

        assertNull(session.mergeWithPrevious(Caret(session.blocks[0].id, 0), UndoHistory()))
    }

    @Test
    fun `a split and a merge return the document to where it started`() {
        // Round-trip, which catches off-by-one errors in both directions at once: if either edit
        // is a character out, the text will not come back.
        val session = DocumentSession(THREE)
        val original = session.text
        val caret = Caret(session.blocks[1].id, "Second".length)

        val afterSplit = session.split(caret, UndoHistory())!!
        session.mergeWithPrevious(afterSplit, UndoHistory())

        assertEquals(original, session.text)
    }

    @Test
    fun `Enter inside a fenced code block adds one line rather than a blank one`() {
        // Enter is consumed unconditionally by the editor, so it arrives here inside a code block
        // too. A blank line does not end a fence, so nothing splits -- but inserting the paragraph
        // separator would still give the author an empty line of code they did not ask for.
        val session = DocumentSession("```\nfun main() {\n}\n```\n")
        val source = session.sourceOf(session.blocks[0].block)
        val caret = Caret(session.blocks[0].id, source.indexOf("fun main() {") + "fun main() {".length)

        session.split(caret, UndoHistory())

        assertEquals(1, session.blocks.size, "The fence was split in two")
        assertEquals("```\nfun main() {\n\n}\n```\n", session.text)
    }

    @Test
    fun `a split in a long document leaves the rest of it alone`() {
        // Structural edits are the ones most likely to defeat the bounded window, because they
        // change the number of blocks rather than the contents of one. If a split renumbered the
        // document, every field below it would be destroyed and recreated.
        val session = DocumentSession(GateFixture.tenThousandWords())
        val target = session.blocks[MIDDLE_BLOCK]
        val countBefore = session.blocks.size
        val headBefore = session.blocks.take(MIDDLE_BLOCK - 1).map { it.id }
        val tailBefore = session.blocks.takeLast(TAIL_SAMPLE).map { it.id }

        session.split(Caret(target.id, 1), UndoHistory())

        assertEquals(countBefore + 1, session.blocks.size, "A split should add exactly one block")
        assertEquals(headBefore, session.blocks.take(MIDDLE_BLOCK - 1).map { it.id })
        assertEquals(tailBefore, session.blocks.takeLast(TAIL_SAMPLE).map { it.id })
    }

    private companion object {
        const val THREE = "First para.\n\nSecond para.\n\nThird para.\n"
        const val MIDDLE_BLOCK = 100
        const val TAIL_SAMPLE = 5
    }
}
