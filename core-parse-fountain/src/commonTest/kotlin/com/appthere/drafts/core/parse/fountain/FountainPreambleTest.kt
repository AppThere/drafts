package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two things that are not elements: the title page and the boneyard.
 *
 * Both are found before anything is split on blank lines, because both contain them. The title
 * page "must be the first thing in the file"; a boneyard "can span everything else".
 */
class FountainPreambleTest {
    @Test
    fun `a title page becomes one block and the document's metadata`() {
        val document = parse(TITLE_PAGE + "\nINT. HOUSE - DAY\n")

        assertEquals("THE LAST BIRTHDAY CARD", document.metadata.title)
        assertEquals("Stu Maschwitz", document.metadata.author)
        assertEquals(listOf(BlockRole.BODY, BlockRole.SCENE_HEADING), document.blocks.map { it.role })
    }

    @Test
    fun `a title page value may be indented under its key`() {
        // "Values may be inline after the colon, or indented on following lines."
        val document = parse("Title:\n    THE SALT ROAD\nCredit: Written by\n\nFADE IN:\n")

        assertEquals("THE SALT ROAD", document.metadata.title)
    }

    @Test
    fun `a screenplay opening with FADE IN has no title page`() {
        // The reason the first key has to be one this application recognises: `FADE IN:` is shaped
        // exactly like a title-page key, and reading it as one would swallow the first scene.
        val document = parse("FADE IN:\n\nINT. HOUSE - DAY\n")

        assertNull(document.metadata.title)
        assertEquals(listOf(BlockRole.ACTION, BlockRole.SCENE_HEADING), document.blocks.map { it.role })
    }

    @Test
    fun `a key that appears later in the document is not a title page`() {
        // "Must be the first thing in the file."
        val document = parse("INT. HOUSE - DAY\n\nTitle: not really\n")

        assertNull(document.metadata.title)
        assertEquals(listOf(BlockRole.SCENE_HEADING, BlockRole.ACTION), document.blocks.map { it.role })
    }

    @Test
    fun `a boneyard is one block however many blank lines it holds`() {
        val source = "Before.\n\n/*\nINT. OLD SCENE - DAY\n\nThis whole scene is cut.\n*/\n\nAfter.\n"
        val document = parse(source)

        assertEquals(listOf(BlockRole.ACTION, BlockRole.NOTE, BlockRole.ACTION), document.blocks.map { it.role })
    }

    @Test
    fun `a boneyard keeps its delimiters in its source`() {
        val source = "/*\nCut.\n*/\n"
        val block = parse(source).blocks.single()

        assertEquals(0, block.source?.start?.value)
        assertEquals(source.trimEnd().length, block.source?.endExclusive?.value)
    }

    @Test
    fun `an unterminated boneyard runs to the end`() {
        // Losing the writer's text would be the only worse answer.
        val document = parse("Before.\n\n/*\nAnd the rest is cut.\n")

        assertEquals(listOf(BlockRole.ACTION, BlockRole.NOTE), document.blocks.map { it.role })
    }

    @Test
    fun `a boneyard opened mid-line is left to the inline layer`() {
        // Treating it as a block would cut the action in half. The delimiters stay in the text
        // until there is an inline pass to take them out.
        val document = parse("She crosses the room. /* beat */ She stops.\n")

        assertEquals(listOf(BlockRole.ACTION), document.blocks.map { it.role })
    }

    @Test
    fun `dual dialogue marks both sides without inventing a role`() {
        // 5.4 makes dual dialogue a two-column layout. What the elements *are* does not change, so
        // the marker is recorded as a class and the roles stay what they were.
        val document = parse("BRICK\nScrew retirement.\n\nSTEEL ^\nScrew retirement.\n")

        assertEquals(
            listOf(BlockRole.CHARACTER, BlockRole.DIALOGUE, BlockRole.CHARACTER, BlockRole.DIALOGUE),
            document.blocks.map { it.role },
        )

        val second = document.blocks.drop(2)
        assertTrue(second.all { it.attrs.hasClass("dual") }, "The second speech was not marked")
        assertTrue(document.blocks.take(2).none { it.attrs.hasClass("dual") }, "The first speech was marked")
    }

    @Test
    fun `the caret marker is not part of the name`() {
        val name = parse("STEEL ^\nScrew retirement.\n").blocks.first()

        assertEquals("STEEL", (name as Paragraph).inlines.plainText())
    }

    private fun parse(source: String) = FountainDocumentParser().parse(source)

    private companion object {
        val TITLE_PAGE =
            """
            |Title:
            |    _**THE LAST BIRTHDAY CARD**_
            |Credit: Written by
            |Author: Stu Maschwitz
            |Draft date: 1/20/2012
            |
            """.trimMargin()
    }
}
