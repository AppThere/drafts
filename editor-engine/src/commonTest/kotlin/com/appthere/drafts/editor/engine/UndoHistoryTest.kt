package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Undo and redo, the last of the `:editor-engine` deliverables in Phase 2.
 *
 * The tests that matter most are the coalescing ones. Undo that steps a character at a time is not
 * undo -- it is retyping what you just typed -- and it is the difference between a feature that
 * works and one that is technically present.
 */
class UndoHistoryTest {
    @Test
    fun `an edit can be undone`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        val at =
            session.blocks[1]
                .block.source!!
                .start.value

        session.editRecording(history, SourceSpan.of(at, at), "New ")
        session.undo(history)

        assertEquals(THREE, session.text)
    }

    @Test
    fun `an undone edit can be redone`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        val at =
            session.blocks[1]
                .block.source!!
                .start.value

        session.editRecording(history, SourceSpan.of(at, at), "New ")
        val edited = session.text
        session.undo(history)
        session.redo(history)

        assertEquals(edited, session.text)
    }

    @Test
    fun `typing a word undoes as one step`() {
        // Six keystrokes, one entry. Undoing letter by letter is the most common way this feature
        // is got wrong, and it is invisible until someone tries to use it.
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        var at =
            session.blocks[1]
                .block.source!!
                .start.value

        "Hello".forEach { character ->
            session.editRecording(history, SourceSpan.of(at, at), character.toString())
            at += 1
        }

        assertEquals(1, history.size, "A typed word should be one undo step")
        session.undo(history)
        assertEquals(THREE, session.text)
    }

    @Test
    fun `a space closes the run so undo lands on word boundaries`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        var at =
            session.blocks[1]
                .block.source!!
                .start.value

        "Hi there".forEach { character ->
            session.editRecording(history, SourceSpan.of(at, at), character.toString())
            at += 1
        }

        assertEquals(EXPECTED_RUNS, history.size)

        session.undo(history)
        assertTrue(session.text.contains("Hi Second"), "Undo removed more than the last word: ${session.text}")
    }

    @Test
    fun `moving the caret away starts a new undo step`() {
        // Two insertions that are not adjacent are two separate things the user did, however
        // quickly they did them.
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        val first =
            session.blocks[0]
                .block.source!!
                .start.value
        val second =
            session.blocks[1]
                .block.source!!
                .start.value

        session.editRecording(history, SourceSpan.of(first, first), "A")
        session.editRecording(history, SourceSpan.of(second + 1, second + 1), "B")

        assertEquals(2, history.size)
    }

    @Test
    fun `backspacing a word undoes as one step`() {
        val session = DocumentSession("First para.\n\nSecond para.\n")
        val history = UndoHistory()
        var at = "First".length

        repeat("First".length) {
            session.editRecording(history, SourceSpan.of(at - 1, at), "")
            at -= 1
        }

        assertEquals(1, history.size, "A backspaced word should be one undo step")
        session.undo(history)
        assertEquals("First para.\n\nSecond para.\n", session.text)
    }

    @Test
    fun `a deletion after an insertion is a separate step`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        val at =
            session.blocks[0]
                .block.source!!
                .start.value

        session.editRecording(history, SourceSpan.of(at, at), "X")
        session.editRecording(history, SourceSpan.of(at, at + 1), "")

        assertEquals(2, history.size)
    }

    @Test
    fun `undo puts the caret where the typing started`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        val at =
            session.blocks[1]
                .block.source!!
                .start.value

        session.editRecording(history, SourceSpan.of(at, at), "New")
        val caret = session.undo(history)!!

        assertEquals(session.blocks[1].id, caret.block)
        assertEquals(0, caret.offset)
    }

    @Test
    fun `undo of a deletion puts the caret at the end of what came back`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()
        val start =
            session.blocks[1]
                .block.source!!
                .start.value

        session.editRecording(history, SourceSpan.of(start, start + "Second".length), "")
        val caret = session.undo(history)!!

        assertEquals("Second".length, caret.offset)
    }

    @Test
    fun `a structural edit undoes as a whole`() {
        // Enter splits a paragraph in two. Undo has to put it back as one, which it does without
        // knowing anything about blocks: the text goes back, and the blocks follow from the text.
        val session = DocumentSession("One two.\n")
        val history = UndoHistory()
        val at = "One ".length

        session.editRecording(history, SourceSpan.of(at, at), "\n\n")
        assertEquals(2, session.blocks.size)

        session.undo(history)

        assertEquals("One two.\n", session.text)
        assertEquals(1, session.blocks.size)
    }

    @Test
    fun `undoing a promotion puts the paragraph back`() {
        val session = DocumentSession("Second para.\n")
        val history = UndoHistory()

        session.editRecording(history, SourceSpan.of(0, 0), "## ")
        assertTrue(session.blocks[0].block is Heading)

        session.undo(history)

        assertFalse(session.blocks[0].block is Heading, "The heading should have gone back to a paragraph")
    }

    @Test
    fun `a new edit after an undo discards the redo`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()

        session.editRecording(history, SourceSpan.of(0, 0), "A")
        session.undo(history)
        assertTrue(history.canRedo)

        session.editRecording(history, SourceSpan.of(0, 0), "B")

        assertFalse(history.canRedo, "The undone future is no longer reachable")
    }

    @Test
    fun `undo and redo do nothing on an empty history`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory()

        assertNull(session.undo(history))
        assertNull(session.redo(history))
        assertEquals(THREE, session.text)
    }

    @Test
    fun `the history does not grow without bound`() {
        val session = DocumentSession(THREE)
        val history = UndoHistory(limit = SMALL_LIMIT)

        repeat(SMALL_LIMIT * 2) { index ->
            // Separated by a space each time, so nothing coalesces and every edit is its own entry.
            session.editRecording(history, SourceSpan.of(0, 0), "x ")
            assertTrue(history.size <= SMALL_LIMIT, "History grew to ${history.size} at edit $index")
        }
    }

    @Test
    fun `a long run of edits undoes back to the start`() {
        // The property that matters, stated directly: whatever was done, undoing all of it returns
        // the document to where it began.
        val session = DocumentSession(THREE)
        val history = UndoHistory()

        session.editRecording(history, SourceSpan.of(0, 0), "## ")
        session.editRecording(history, SourceSpan.of(3, 3), "Title")
        val at =
            session.blocks
                .last()
                .block.source!!
                .start.value
        session.editRecording(history, SourceSpan.of(at, at + 5), "")

        while (history.canUndo) session.undo(history)

        assertEquals(THREE, session.text)
    }

    private companion object {
        const val THREE = "First para.\n\nSecond para.\n\nThird para.\n"

        /** "Hi " and "there": the space joins the run it ends, so undo leaves it behind. */
        const val EXPECTED_RUNS = 2
        const val SMALL_LIMIT = 8
    }
}
