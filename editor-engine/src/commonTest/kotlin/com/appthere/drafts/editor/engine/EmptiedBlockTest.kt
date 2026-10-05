package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Deleting every word of a block leaves the reader in it.
 *
 * The block itself is gone -- there is no text for the parser to find -- but the caret is still in
 * it, so the room left in its place carries its identity. Without that the field closed under the
 * reader, and a document of one paragraph, emptied, had nowhere left to type.
 */
class EmptiedBlockTest {
    @Test
    fun `the only paragraph emptied is still there to type into`() {
        val session = DocumentSession("Hello")
        val id = session.blocks.single().id

        session.deleteAllOf("Hello")

        assertEquals("", session.text)
        assertEquals(listOf(id), session.blocks.map { it.id })
    }

    @Test
    fun `a paragraph emptied between two others keeps its place`() {
        val session = DocumentSession("Above\n\nMiddle\n\nBelow")
        val id = session.idOf("Middle")

        session.deleteAllOf("Middle")

        assertEquals(listOf(id), session.emptyBlocks().map { it.id })
        assertEquals(3, session.blocks.size)
    }

    @Test
    fun `the last paragraph emptied keeps its place`() {
        val session = DocumentSession("Above\n\nBelow")
        val id = session.idOf("Below")

        session.deleteAllOf("Below")

        assertEquals(listOf(id), session.emptyBlocks().map { it.id })
    }

    @Test
    fun `what is typed into an emptied paragraph is that paragraph again`() {
        val session = DocumentSession("Hello")
        val id = session.blocks.single().id
        session.deleteAllOf("Hello")

        session.edit(SourceSpan.of(0, 0), "Again")

        assertEquals(listOf(id), session.blocks.map { it.id })
        assertEquals("Again", session.sourceOf(session.blocks.single().block))
    }

    @Test
    fun `a screenplay's only line emptied is still there to type into`() {
        val session = DocumentSession("Steel walks in.", BlockParser.Fountain())
        val id = session.blocks.single().id

        session.deleteAllOf("Steel walks in.")

        assertEquals(listOf(id), session.blocks.map { it.id })
    }

    @Test
    fun `dialogue emptied leaves its line under the name`() {
        // The text leaves no blank line there -- "STEEL\n" -- so no room is found; one is kept.
        val session = DocumentSession("STEEL\nNot yet.", BlockParser.Fountain())
        val id = session.idOf("Not yet.")

        session.deleteAllOf("Not yet.")

        val kept = session.emptyBlocks().single()
        assertEquals(id, kept.id)
        assertEquals(BlockRole.DIALOGUE, kept.block.role)
        assertEquals(
            "STEEL\n".length,
            kept.block.source
                ?.start
                ?.value,
        )
    }

    @Test
    fun `a block emptied by a deletion wider than it is not kept`() {
        // Deleting the paragraph *and* its blank line is removing it, not emptying it.
        val session = DocumentSession("Above\n\nMiddle\n\nBelow")
        val id = session.idOf("Middle")

        session.edit(SourceSpan.of("Above".length, "Above\n\nMiddle".length), "")

        assertTrue(session.blocks.none { it.id == id })
        assertEquals("Above\n\nBelow", session.text)
    }

    private fun DocumentSession.idOf(source: String) = blocks.first { sourceOf(it.block) == source }.id

    private fun DocumentSession.deleteAllOf(source: String) {
        val span = assertNotNull(blocks.first { sourceOf(it.block) == source }.block.source)
        edit(span, "")
    }

    private fun DocumentSession.emptyBlocks() = blocks.filter { it.block.source?.length == 0 }
}
