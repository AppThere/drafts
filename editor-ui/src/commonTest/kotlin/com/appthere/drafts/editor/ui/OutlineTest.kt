package com.appthere.drafts.editor.ui

import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 10.1's outline: "the heading/scene hierarchy as a navigable list".
 *
 * What is asserted is the hierarchy and the positions, which is what makes the list navigable. How
 * it looks is the panel's, and what it says out loud is the panel's too.
 */
class OutlineTest {
    @Test
    fun `the outline is the headings in document order`() {
        val outline = outlineOf(DocumentSession(DOCUMENT).blocks)

        assertEquals(
            listOf("The salt road", "Chapter one", "A morning", "Chapter two"),
            outline.map { it.text },
        )
    }

    @Test
    fun `each entry knows how deep it is`() {
        val outline = outlineOf(DocumentSession(DOCUMENT).blocks)

        assertEquals(listOf(1, 2, 3, 2), outline.map { it.level })
    }

    @Test
    fun `each entry knows which block it is so the document can be scrolled to it`() {
        val blocks = DocumentSession(DOCUMENT).blocks
        val outline = outlineOf(blocks)

        outline.forEach { entry ->
            assertEquals(entry.id, blocks[entry.index].id, "Entry '${entry.text}' pointed at the wrong block")
        }
    }

    @Test
    fun `inline markup is resolved rather than read out`() {
        // Someone hearing the outline read aloud should hear the words, not the asterisks.
        val outline = outlineOf(DocumentSession("## The *salt* road\n").blocks)

        assertEquals("The salt road", outline.single().text)
    }

    @Test
    fun `a heading with nothing after it is still a place in the document`() {
        // The common state while writing one. Dropping it would make the outline jump over the
        // line being typed.
        val outline = outlineOf(DocumentSession("# Title\n\n##\n\nWords.\n").blocks)

        assertEquals(2, outline.size)
        assertEquals("", outline.last().text)
    }

    @Test
    fun `a document with no headings has no outline`() {
        assertTrue(outlineOf(DocumentSession("Just words.\n\nAnd more.\n").blocks).isEmpty())
    }

    @Test
    fun `nothing but headings is in it`() {
        // A quote or a list is not a place in the hierarchy, and an outline that listed everything
        // would be the document again.
        val outline = outlineOf(DocumentSession("# Title\n\n> A quote.\n\n- An item\n\n## Next\n").blocks)

        assertEquals(listOf("Title", "Next"), outline.map { it.text })
    }

    @Test
    fun `a screenplay's scenes are in the outline under its acts`() {
        // 10.1's "heading/scene hierarchy". A Fountain section is a heading; a scene sits one level
        // under the section above it, and is named as a scene rather than as a heading.
        val script = "# Act One\n\nINT. KITCHEN - NIGHT\n\nA kettle sings.\n\nEXT. GARDEN - DAWN\n"
        val outline = outlineOf(DocumentSession(script, BlockParser.Fountain()).blocks)

        assertEquals(listOf("Act One", "INT. KITCHEN - NIGHT", "EXT. GARDEN - DAWN"), outline.map { it.text })
        assertEquals(listOf(1, 2, 2), outline.map { it.level })
        assertEquals(listOf(false, true, true), outline.map { it.scene })
    }

    @Test
    fun `a script with no sections is its scenes at the top level`() {
        val outline =
            outlineOf(DocumentSession("INT. KITCHEN - NIGHT\n\nEXT. GARDEN - DAWN\n", BlockParser.Fountain()).blocks)

        assertEquals(listOf(1, 1), outline.map { it.level })
    }

    private companion object {
        val DOCUMENT =
            """
            |# The salt road
            |
            |An opening paragraph.
            |
            |## Chapter one
            |
            |Words.
            |
            |### A morning
            |
            |More words.
            |
            |## Chapter two
            |
            |The end.
            |
            """.trimMargin()
    }
}
