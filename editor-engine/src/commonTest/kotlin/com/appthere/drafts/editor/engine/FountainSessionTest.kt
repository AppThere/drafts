package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.parse.fountain.FountainDocumentParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A screenplay open in the editor: read as Fountain, and reread as little as possible after an edit.
 *
 * The incremental reparse is only worth having if it gives the answer a whole parse would. Fountain
 * makes that harder than Markdown -- a character is an uppercase line with dialogue under it, so the
 * window is widened to whole chunks before it is read -- and the property test here holds every edit
 * to the whole-parse answer rather than trusting the widening.
 */
class FountainSessionTest {
    @Test
    fun `a screenplay is read as Fountain`() {
        val session =
            DocumentSession("INT. KITCHEN - NIGHT\n\nSTEEL\nSo much for retirement.\n", BlockParser.Fountain())

        assertEquals(
            listOf(BlockRole.SCENE_HEADING, BlockRole.CHARACTER, BlockRole.DIALOGUE),
            session.parsedRoles(),
        )
    }

    @Test
    fun `a name becomes a character once dialogue is typed under it`() {
        // Alone, an uppercase line is action. The line typed under it is outside the name's block,
        // so only a window widened to the whole chunk sees the two together.
        val session = DocumentSession("INT. KITCHEN - NIGHT\n\nSTEEL", BlockParser.Fountain())
        val end = session.text.length

        session.edit(SourceSpan.of(end, end), "\nSo much for retirement.")

        assertEquals(listOf(BlockRole.SCENE_HEADING, BlockRole.CHARACTER, BlockRole.DIALOGUE), session.parsedRoles())
    }

    @Test
    fun `every edit across a script reads as the whole parse would`() {
        // A letter, a line break and a blank line, typed in turn at every line start of the script:
        // the edits that change what a chunk is. After each, the incremental blocks must be the
        // blocks a fresh parse of the same text finds.
        listOf("x", "\n", "\n\n", " \n").forEach { typed ->
            lineStarts(SCRIPT).forEach { at ->
                val session = DocumentSession(SCRIPT, BlockParser.Fountain())

                session.edit(SourceSpan.of(at, at), typed)

                val whole = FountainDocumentParser().parse(session.text).blocks.map { it.role to it.source }
                assertEquals(
                    whole,
                    session.parsedRoleSpans(),
                    "Typing ${typed.escaped()} at $at read differently from a whole parse",
                )
            }
        }
    }

    @Test
    fun `choosing fountain rereads the same text`() {
        // 7.4: "choosing Fountain re-interprets the same text as Fountain". In Markdown "# Act One"
        // is a heading; in Fountain it is a section.
        val session = DocumentSession("# Act One\n\nINT. KITCHEN - NIGHT\n")

        session.reinterpretAs(BlockParser.Fountain())

        assertEquals("# Act One\n\nINT. KITCHEN - NIGHT\n", session.text)
        assertEquals(listOf(BlockRole.SECTION, BlockRole.SCENE_HEADING), session.parsedRoles())
    }

    /** The blocks the parser found, without the empty paragraphs the editor adds between them. */
    private fun DocumentSession.parsedRoles() = parsedBlocks().map { it.role }

    private fun DocumentSession.parsedRoleSpans() = parsedBlocks().map { it.role to it.source }

    private fun DocumentSession.parsedBlocks() = blocks.map { it.block }.filter { (it.source?.length ?: 0) > 0 }

    private fun lineStarts(text: String): List<Int> =
        listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

    private fun String.escaped() = replace("\n", "\\n")

    private companion object {
        val SCRIPT =
            """
            Title: The Window

            INT. KITCHEN - NIGHT

            A kettle starts to sing.

            STEEL
            (quietly)
            So much for retirement.

            And yet here we are.

            JONES
            Here we are.

            CUT TO:
            """.trimIndent() + "\n"
    }
}
