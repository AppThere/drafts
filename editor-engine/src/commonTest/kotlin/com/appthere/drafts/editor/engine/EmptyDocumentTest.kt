package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A document with nothing in it still has somewhere to type.
 *
 * `appthere-drafts.md` 7.4 asks for it twice over: a new document is "ready to type into", and
 * "Nothing stands between launching the app and writing." A document parsed from no text has no
 * blocks, and the editor draws one field per block -- so with none, there is no field, no caret and
 * no row to tap. Found by launching the application on a device and being unable to type a word
 * into the untitled document it had just opened.
 *
 * The same state is reached by selecting everything in a document and deleting it, where being
 * unable to start again would be worse.
 */
class EmptyDocumentTest {
    @Test
    fun `a document with no text still has a block to type into`() {
        assertEquals(1, DocumentSession("").blocks.size)
    }

    @Test
    fun `the empty block claims no text of its own`() {
        // The block is a place to put a caret, not content. `text` is what 8.2 digests and what the
        // serialiser writes, and a document that reported a paragraph it did not have would save a
        // blank line nobody typed.
        val session = DocumentSession("")

        assertEquals("", session.text)
        assertEquals(
            SourceSpan.of(0, 0),
            session.blocks
                .single()
                .block.source,
        )
    }

    @Test
    fun `the first keystroke lands in that block rather than a new one`() {
        // The id has to survive, or the field the reader is typing into is destroyed and recreated
        // after the first character -- which on a phone also takes the keyboard with it.
        val session = DocumentSession("")
        val before = session.blocks.single().id

        session.edit(SourceSpan.of(0, 0), "H")

        assertEquals("H", session.text)
        assertEquals(before, session.blocks.single().id)
    }

    @Test
    fun `deleting every word leaves somewhere to start again`() {
        val session = DocumentSession("A first draft.\n")

        session.edit(SourceSpan.of(0, session.text.length), "")

        assertEquals("", session.text)
        assertEquals(1, session.blocks.size)
        assertTrue(session.sourceOf(session.blocks.single().block).isEmpty())
    }

    @Test
    fun `typing again after deleting everything works`() {
        // The case the empty block exists for, end to end.
        val session = DocumentSession("Gone.\n")
        session.edit(SourceSpan.of(0, session.text.length), "")

        session.edit(SourceSpan.of(0, 0), "Back")

        assertEquals("Back", session.text)
        assertEquals("Back", session.sourceOf(session.blocks.single().block))
    }

    @Test
    fun `a document with words has exactly its own blocks`() {
        // The empty case must not add a block to documents that have some.
        assertEquals(2, DocumentSession("One.\n\nTwo.\n").blocks.size)
    }
}
