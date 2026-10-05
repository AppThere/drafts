package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Enter in a screenplay: a speech is written a line at a time, so Enter under a name or a
 * parenthetical opens the dialogue line rather than the blank line that would end the speech.
 */
class FountainEnterTest {
    private val history = UndoHistory()

    @Test
    fun `enter after a name opens a dialogue line under it`() {
        val session = DocumentSession("INT. KITCHEN - NIGHT\n\nSTEEL", BlockParser.Fountain())

        val caret = session.enterAtEndOf("STEEL")

        assertEquals("INT. KITCHEN - NIGHT\n\nSTEEL\n", session.text)
        assertEquals(BlockRole.DIALOGUE, session.blockAt(caret).role)
        assertEquals(0, session.blockAt(caret).source?.length)
    }

    @Test
    fun `what is typed on the opened line is the name's dialogue`() {
        val session = DocumentSession("INT. KITCHEN - NIGHT\n\nSTEEL", BlockParser.Fountain())
        val caret = session.enterAtEndOf("STEEL")

        session.type(caret, "So much for retirement.")

        assertEquals("INT. KITCHEN - NIGHT\n\nSTEEL\nSo much for retirement.", session.text)
        assertEquals(listOf(BlockRole.SCENE_HEADING, BlockRole.CHARACTER, BlockRole.DIALOGUE), session.parsedRoles())
        // The line typed into is the one the caret was given, so its field was not rebuilt.
        assertEquals(caret.block, session.blocks.last().id)
    }

    @Test
    fun `a speech can be opened above the rest of the script`() {
        val session = DocumentSession("STEEL\n\nHe leaves.\n", BlockParser.Fountain())
        val caret = session.enterAtEndOf("STEEL")

        session.type(caret, "Not yet.")

        assertEquals("STEEL\nNot yet.\n\nHe leaves.\n", session.text)
        assertEquals(listOf(BlockRole.CHARACTER, BlockRole.DIALOGUE, BlockRole.ACTION), session.parsedRoles())
    }

    @Test
    fun `enter after a parenthetical opens the dialogue it introduces`() {
        val session = DocumentSession("STEEL\n(quietly)", BlockParser.Fountain())
        val caret = session.enterAtEndOf("(quietly)")

        session.type(caret, "Not yet.")

        assertEquals(
            listOf(BlockRole.CHARACTER, BlockRole.PARENTHETICAL, BlockRole.DIALOGUE),
            session.parsedRoles(),
        )
    }

    @Test
    fun `enter on the empty dialogue line ends the speech instead`() {
        // A name followed by action, which is how a screenwriting application does it: Enter twice.
        val session = DocumentSession("STEEL", BlockParser.Fountain())
        val opened = session.enterAtEndOf("STEEL")

        val caret = assertNotNull(session.split(opened, history))
        session.type(caret, "He leaves.")

        assertEquals("STEEL\n\nHe leaves.", session.text)
        assertEquals(listOf(BlockRole.ACTION, BlockRole.ACTION), session.parsedRoles())
    }

    @Test
    fun `backspace on the empty dialogue line takes the line break back`() {
        val session = DocumentSession("STEEL", BlockParser.Fountain())
        val opened = session.enterAtEndOf("STEEL")

        val caret = session.mergeWithPrevious(opened, history)

        assertEquals("STEEL", session.text)
        assertEquals(Caret(session.blocks.single().id, "STEEL".length), caret)
    }

    @Test
    fun `undoing the enter closes the line it opened`() {
        val session = DocumentSession("STEEL", BlockParser.Fountain())
        session.enterAtEndOf("STEEL")

        session.undo(history)

        assertEquals("STEEL", session.text)
        assertEquals(1, session.blocks.size)
    }

    @Test
    fun `enter after a line of action is a blank line`() {
        val session = DocumentSession("Steel walks in.", BlockParser.Fountain())

        session.enterAtEndOf("Steel walks in.")

        assertEquals("Steel walks in.\n\n", session.text)
    }

    @Test
    fun `enter after dialogue ends the speech`() {
        val session = DocumentSession("STEEL\nNot yet.", BlockParser.Fountain())

        session.enterAtEndOf("Not yet.")

        assertEquals("STEEL\nNot yet.\n\n", session.text)
    }

    @Test
    fun `enter after a name that is already speaking is not a dialogue line`() {
        // Breaking the line there would put a blank line between the name and its speech. Enter is
        // the blank line it always was; what to do instead is a question for later.
        val session = DocumentSession("STEEL\nNot yet.", BlockParser.Fountain())

        session.enterAtEndOf("STEEL")

        assertEquals("STEEL\n\n\nNot yet.", session.text)
    }

    @Test
    fun `markdown keeps its blank line after an uppercase line`() {
        val session = DocumentSession("STEEL")

        session.enterAtEndOf("STEEL")

        assertEquals("STEEL\n\n", session.text)
    }

    /** Enter at the end of the block whose source is [source]. The caret after it. */
    private fun DocumentSession.enterAtEndOf(source: String): Caret {
        val block = blocks.first { sourceOf(it.block) == source }
        return assertNotNull(split(Caret(block.id, source.length), history))
    }

    private fun DocumentSession.type(
        caret: Caret,
        typed: String,
    ) {
        val at = assertNotNull(offsetIn(caret))
        editRecording(history, SourceSpan.of(at, at), typed)
    }

    private fun DocumentSession.blockAt(caret: Caret) = blocks.first { it.id == caret.block }.block

    private fun DocumentSession.parsedRoles() =
        blocks.map { it.block }.filter { (it.source?.length ?: 0) > 0 }.map { it.role }
}
