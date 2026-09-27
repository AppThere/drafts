package com.appthere.drafts.editor.ui

import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every edit to the text moves [EditorState.revision]; nothing else does.
 *
 * This is what 8.4's `dirty` is built on, and the failure mode is silent in both directions. A
 * mutator that forgot to bump it produces a document that reports itself saved while holding unsaved
 * work. A caret move that bumped it produces a document that can never be clean, so the badge says
 * `dirty` forever and stops meaning anything.
 */
class EditorRevisionTest {
    @Test
    fun `a fresh document has made no edits`() {
        assertEquals(0, state().revision)
    }

    @Test
    fun `typing moves the revision`() {
        val editor = state()
        val block = editor.blocks.first()
        editor.place(Caret(block.id, 0))

        editor.replace(requireNotNull(block.block.source), "Edited.", "Edited.".length)

        assertTrue(editor.revision > 0, "Typing left the revision at ${editor.revision}")
    }

    @Test
    fun `splitting moves the revision`() {
        val editor = state()
        editor.place(Caret(editor.blocks.first().id, 1))
        val before = editor.revision

        editor.split()

        assertTrue(editor.revision > before, "Enter left the revision at ${editor.revision}")
    }

    @Test
    fun `merging moves the revision`() {
        val editor = state()
        editor.place(Caret(editor.blocks.last().id, 0))
        val before = editor.revision

        assertTrue(editor.mergeWithPrevious(), "The merge did not happen so the test proves nothing")
        assertTrue(editor.revision > before, "Backspace left the revision at ${editor.revision}")
    }

    @Test
    fun `deleting a selection moves the revision`() {
        val editor = state()
        editor.beginSelection(Caret(editor.blocks.first().id, 0))
        editor.extendSelection(Caret(editor.blocks.first().id, THREE))
        editor.endSelection()
        val before = editor.revision

        assertTrue(editor.deleteSelection(), "Nothing was selected so the test proves nothing")
        assertTrue(editor.revision > before, "Delete left the revision at ${editor.revision}")
    }

    @Test
    fun `undo moves the revision`() {
        // Undo changes the text, so it changes whether the document matches its file. A document
        // that was clean before an undo is not clean after one.
        val editor = state()
        editor.place(Caret(editor.blocks.first().id, 1))
        editor.split()
        val before = editor.revision

        assertTrue(editor.undo(), "There was nothing to undo so the test proves nothing")
        assertTrue(editor.revision > before, "Undo left the revision at ${editor.revision}")
    }

    @Test
    fun `redo moves the revision`() {
        val editor = state()
        editor.place(Caret(editor.blocks.first().id, 1))
        editor.split()
        editor.undo()
        val before = editor.revision

        assertTrue(editor.redo(), "There was nothing to redo so the test proves nothing")
        assertTrue(editor.revision > before, "Redo left the revision at ${editor.revision}")
    }

    @Test
    fun `moving the caret does not move the revision`() {
        // The other direction. Placing a caret and crossing a block boundary change what the reader
        // is looking at and not what the document says.
        val editor = state()
        editor.place(Caret(editor.blocks.last().id, 0))
        val before = editor.revision

        editor.moveToPrevious()
        editor.moveToNext()
        editor.place(Caret(editor.blocks.first().id, 0))

        assertEquals(before, editor.revision)
    }

    @Test
    fun `selecting does not move the revision`() {
        val editor = state()
        val before = editor.revision

        editor.beginSelection(Caret(editor.blocks.first().id, 0))
        editor.extendSelection(Caret(editor.blocks.last().id, 1))
        editor.endSelection()
        editor.selectAll()

        assertEquals(before, editor.revision)
    }

    private fun state() = EditorState(DocumentSession("First paragraph.\n\nSecond paragraph.\n"))

    private companion object {
        const val THREE = 3
    }
}
