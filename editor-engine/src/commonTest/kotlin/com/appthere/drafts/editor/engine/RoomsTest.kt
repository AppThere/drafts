package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Enter at the end of a paragraph, and the empty paragraph it makes.
 *
 * `appthere-drafts.md` 4.3: "pressing Enter splits". Enter at the end of the last paragraph used to
 * insert a blank line that parsed as nothing, leaving the caret nowhere new: the next words joined
 * the paragraph above. The blank lines between blocks are now read for room (`roomsIn`), and these
 * are the cases a writer meets first.
 */
class RoomsTest {
    @Test
    fun `enter at the end of the document makes a paragraph to type into`() {
        val session = DocumentSession("Line one")
        val history = UndoHistory()

        val caret = assertNotNull(session.split(Caret(session.blocks.single().id, "Line one".length), history))

        assertEquals(2, session.blocks.size)
        assertEquals(session.blocks.last().id, caret.block)
        assertEquals(0, caret.offset)
    }

    @Test
    fun `what is typed there is a new paragraph, not more of the old one`() {
        val session = DocumentSession("Line one")
        val history = UndoHistory()
        val caret = assertNotNull(session.split(Caret(session.blocks.single().id, "Line one".length), history))
        val room = assertNotNull(session.offsetIn(caret))

        session.edit(SourceSpan.of(room, room), "Line two")

        assertEquals("Line one\n\nLine two", session.text)
        assertEquals(listOf("Line one", "Line two"), session.blocks.map { session.sourceOf(it.block) })
    }

    @Test
    fun `the empty paragraph keeps its identity when typed into`() {
        // The field the reader types into is keyed on this id. Losing it after the first character
        // would destroy the field mid-word -- and on a phone take the keyboard down with it.
        val session = DocumentSession("Line one")
        val caret = assertNotNull(session.split(Caret(session.blocks.single().id, "Line one".length), UndoHistory()))
        val room = assertNotNull(session.offsetIn(caret))

        session.edit(SourceSpan.of(room, room), "L")

        assertEquals(caret.block, session.blocks.last().id)
    }

    @Test
    fun `enter at the end of a paragraph in the middle makes one between`() {
        val session = DocumentSession("First.\n\nSecond.\n")
        val first = session.blocks.first()

        val caret = assertNotNull(session.split(Caret(first.id, "First.".length), UndoHistory()))

        assertEquals(3, session.blocks.size)
        assertEquals(session.blocks[1].id, caret.block)
        assertEquals("", session.sourceOf(session.blocks[1].block))
    }

    @Test
    fun `the paragraph below keeps its identity through the split`() {
        // Blocks wholly after an edit are matched from the back, so the paragraph below the split
        // is the same row afterwards rather than one destroyed and rebuilt.
        val session = DocumentSession("First.\n\nSecond.\n")
        val second = session.blocks.last().id

        session.split(Caret(session.blocks.first().id, "First.".length), UndoHistory())

        assertEquals(second, session.blocks.last().id)
    }

    @Test
    fun `an empty paragraph claims no text`() {
        // 8.2 digests the text and the serialiser writes it; a room is somewhere to put the caret,
        // and must not add a byte the reader did not type.
        val session = DocumentSession("First.\n\n")

        assertEquals("First.\n\n", session.text)
        assertEquals(
            SourceSpan.of(8, 8),
            session.blocks
                .last()
                .block.source,
        )
    }

    @Test
    fun `a file ending in one line break has no empty paragraph at the end`() {
        // How nearly every file on disk ends. Showing an empty paragraph under each of them would be
        // a blank row nobody wrote.
        assertEquals(1, DocumentSession("A paragraph.\n").blocks.size)
    }

    @Test
    fun `enter twice at the end gives two empty paragraphs and the caret in the second`() {
        val session = DocumentSession("Line one")
        val history = UndoHistory()
        val once = assertNotNull(session.split(Caret(session.blocks.single().id, "Line one".length), history))

        val twice = assertNotNull(session.split(once, history))

        assertEquals(3, session.blocks.size)
        assertEquals(session.blocks.last().id, twice.block)
    }
}
