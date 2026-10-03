package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fountain 1.1's elements, as `fountain.md` describes them.
 *
 * Mostly the spec's own examples, because they are the cases its authors chose to be unambiguous
 * about. The ones that are not the spec's are the four `IMPLEMENTATION-PLAN.md` names for Phase 7:
 * "uppercase action vs character, `CUT TO: ` with trailing space, `..` vs forced scene heading,
 * blank-line-with-space inside dialogue".
 */
class FountainParserTest {
    @Test
    fun `a scene heading is recognised by its prefix`() {
        assertEquals(BlockRole.SCENE_HEADING, roleOf("EXT. BRICK'S POOL - DAY\n"))
        assertEquals(BlockRole.SCENE_HEADING, roleOf("INT. HOUSE - DAY\n"))
        assertEquals(BlockRole.SCENE_HEADING, roleOf("EST. RIVER - DAWN\n"))
        assertEquals(BlockRole.SCENE_HEADING, roleOf("I/E. CAR - NIGHT\n"))
    }

    @Test
    fun `a word that merely starts with a prefix is not a scene heading`() {
        // INTERIOR is not INT, and a parser that matched on the letters alone would read every
        // paragraph opening with one of six common words as a scene.
        assertEquals(BlockRole.ACTION, roleOf("INTERIOR DESIGN was never her calling.\n"))
        assertEquals(BlockRole.ACTION, roleOf("ESTABLISHING a rhythm takes weeks.\n"))
    }

    @Test
    fun `a forced scene heading loses its period but keeps its source`() {
        val block = parse(".SNIPER SCOPE POV\n").blocks.single()

        assertEquals(BlockRole.SCENE_HEADING, block.role)
        assertEquals("SNIPER SCOPE POV", block.words())
        assertEquals(0, block.source?.start?.value)
        assertEquals(".SNIPER SCOPE POV".length, block.source?.endExclusive?.value)
    }

    @Test
    fun `two periods are an ellipsis and not a forced scene heading`() {
        // "A line beginning with two or more periods is *not* a forced scene heading -- that
        // reserves `..` for action text starting with an ellipsis."
        assertEquals(BlockRole.ACTION, roleOf("..and then she was gone.\n"))
        assertEquals(BlockRole.ACTION, roleOf("...a long pause.\n"))
    }

    @Test
    fun `a scene number is read out of the heading and kept in the source`() {
        // "Scene numbers: appended in `#...#` at end of line." Archival rather than part of the
        // slugline, so the words stop before it and the source does not.
        val block = parse("INT. HOUSE - DAY #1#\n").blocks.single()

        assertEquals("1", block.attrs["scene"])
        assertEquals("INT. HOUSE - DAY", block.words().trimEnd())
        assertEquals("INT. HOUSE - DAY #1#".length, block.source?.endExclusive?.value)
    }

    @Test
    fun `a scene number may be alphanumeric with hyphens and periods`() {
        assertEquals("A1.2", parse("EXT. PATIO - DAY #A1.2#\n").blocks.single().attrs["scene"])
        assertEquals("1-A", parse("EXT. PATIO - DAY #1-A#\n").blocks.single().attrs["scene"])
    }

    @Test
    fun `a hash that is not a scene number is left in the heading`() {
        val block = parse("INT. HOUSE - DAY #not a number#\n").blocks.single()

        assertNull(block.attrs["scene"])
    }

    @Test
    fun `a forced scene heading may carry a number too`() {
        val block = parse(".SNIPER SCOPE POV #7#\n").blocks.single()

        assertEquals("7", block.attrs["scene"])
        assertEquals("SNIPER SCOPE POV", block.words().trimEnd())
    }

    @Test
    fun `an uppercase line on its own is action and not a character`() {
        // "A line in all uppercase, preceded by a blank line, **not** followed by a blank line" is
        // a character. One with a blank line after it has nobody to speak.
        assertEquals(BlockRole.ACTION, roleOf("THE DOOR SLAMS.\n"))
    }

    @Test
    fun `an uppercase line with words under it is a character`() {
        val blocks = parse("STEEL\nSo much for retirement.\n").blocks

        assertEquals(listOf(BlockRole.CHARACTER, BlockRole.DIALOGUE), blocks.map { it.role })
        assertEquals("STEEL", blocks.first().words())
        assertEquals("So much for retirement.", blocks.last().words())
    }

    @Test
    fun `a character may carry an extension`() {
        assertEquals(BlockRole.CHARACTER, parse("MOM (V.O.)\nCome inside.\n").blocks.first().role)
        assertEquals(BlockRole.CHARACTER, parse("HANS (on the radio)\nWe are ready.\n").blocks.first().role)
    }

    @Test
    fun `a forced character may be lowercase`() {
        val blocks = parse("@McCLANE\nYippee ki-yay.\n").blocks

        assertEquals(listOf(BlockRole.CHARACTER, BlockRole.DIALOGUE), blocks.map { it.role })
        assertEquals("McCLANE", blocks.first().words())
    }

    @Test
    fun `a parenthetical sits between the name and the words`() {
        val blocks = parse("STEEL\n(starting the engine)\nSo much for retirement!\n").blocks

        assertEquals(
            listOf(BlockRole.CHARACTER, BlockRole.PARENTHETICAL, BlockRole.DIALOGUE),
            blocks.map { it.role },
        )
    }

    @Test
    fun `a blank line with a space in it keeps the speech together`() {
        // The one rule that breaks a naive split, and the reason chunks remember their separator.
        val blocks = parse("STEEL\nSo much for retirement.\n \nAnd yet here we are.\n").blocks

        assertEquals(
            listOf(BlockRole.CHARACTER, BlockRole.DIALOGUE, BlockRole.DIALOGUE),
            blocks.map { it.role },
        )
    }

    @Test
    fun `an empty blank line ends the speech`() {
        val blocks = parse("STEEL\nSo much for retirement.\n\nHe walks away.\n").blocks

        assertEquals(listOf(BlockRole.CHARACTER, BlockRole.DIALOGUE, BlockRole.ACTION), blocks.map { it.role })
    }

    @Test
    fun `a transition ends in the suffix`() {
        assertEquals(BlockRole.TRANSITION, roleOf("CUT TO:\n"))
        assertEquals(BlockRole.TRANSITION, roleOf("        DISSOLVE TO:\n"))
    }

    @Test
    fun `a transition with a trailing space is action`() {
        // "Adding a space after the colon (`CUT TO: `) makes the line parse as Action -- a
        // documented escape."
        assertEquals(BlockRole.ACTION, roleOf("CUT TO: \n"))
    }

    @Test
    fun `a forced transition need not be uppercase`() {
        val block = parse("> Burn to White.\n").blocks.single()

        assertEquals(BlockRole.TRANSITION, block.role)
        assertEquals("Burn to White.", block.words())
    }

    @Test
    fun `an angle bracket on both sides is centred text`() {
        val block = parse("> THE END <\n").blocks.single()

        assertEquals(BlockRole.CENTERED, block.role)
    }

    @Test
    fun `three equals signs are a page break and one is a synopsis`() {
        assertEquals(BlockRole.PAGE_BREAK, roleOf("===\n"))
        assertEquals(BlockRole.PAGE_BREAK, roleOf("=====\n"))

        val synopsis = parse("= Steel arrives at the pool.\n").blocks.single()
        assertEquals(BlockRole.SYNOPSIS, synopsis.role)
        assertEquals("Steel arrives at the pool.", synopsis.words())
    }

    @Test
    fun `a section is a heading with a depth and a role of its own`() {
        val blocks = parse("# Act I\n\n## Sequence A\n\n### Scene 1\n").blocks

        assertEquals(listOf(1, 2, 3), blocks.map { (it as Heading).level })
        assertTrue(blocks.all { it.role == BlockRole.SECTION })
        assertEquals(listOf("Act I", "Sequence A", "Scene 1"), blocks.map { it.words() })
    }

    @Test
    fun `each lyric line is its own element`() {
        val blocks = parse("~Willy Wonka! Willy Wonka!\n~The amazing chocolatier!\n").blocks

        assertEquals(listOf(BlockRole.LYRIC, BlockRole.LYRIC), blocks.map { it.role })
        assertEquals("Willy Wonka! Willy Wonka!", blocks.first().words())
    }

    @Test
    fun `forced action is action even when it looks like something else`() {
        // The reason the forcing character exists: "Necessary when a line would otherwise be read
        // as a character name (all uppercase) or a scene heading."
        assertEquals(BlockRole.ACTION, roleOf("!INT. HOUSE - DAY\n"))

        val block = parse("!THE DOOR SLAMS.\nShe does not look back.\n").blocks.single()
        assertEquals(BlockRole.ACTION, block.role)
    }

    @Test
    fun `action is the fallback`() {
        assertEquals(BlockRole.ACTION, roleOf("She crosses the room and opens the door.\n"))
    }

    @Test
    fun `leading whitespace in action is kept`() {
        // "Leading whitespace in Action is preserved -- this is the mechanism for hand-positioned
        // text."
        assertEquals("        hand-positioned", parse("        hand-positioned\n").blocks.single().words())
    }

    @Test
    fun `every block's source is the text it was made from`() {
        val source = SCREENPLAY
        val document = parse(source)

        document.blocks.forEach { block ->
            val span = requireNotNull(block.source) { "A parsed block had no source: $block" }
            assertTrue(
                span.endExclusive.value <= source.length,
                "A span ran past the end of the document: $span",
            )
            assertTrue(span.start.value < span.endExclusive.value, "An empty span: $span")
        }
    }

    @Test
    fun `the blocks cover the document in order and do not overlap`() {
        // What makes round-tripping free: everything between two blocks is whitespace nobody
        // claimed, so a serialiser can put the original bytes back.
        val document = parse(SCREENPLAY)
        val spans = document.blocks.mapNotNull { it.source }

        spans.zipWithNext().forEach { (first, second) ->
            assertTrue(
                first.endExclusive.value <= second.start.value,
                "Blocks overlapped: $first then $second",
            )
            assertTrue(
                SCREENPLAY.substring(first.endExclusive.value, second.start.value).isBlank(),
                "Something between two blocks was not whitespace",
            )
        }
    }

    private fun parse(source: String) = FountainDocumentParser().parse(source)

    private fun roleOf(source: String) = parse(source).blocks.single().role

    private fun Block.words(): String =
        when (this) {
            is Heading -> inlines.plainText()
            is com.appthere.drafts.core.model.Paragraph -> inlines.plainText()
            else -> ""
        }

    private companion object {
        val SCREENPLAY =
            """
            |INT. HOUSE - DAY
            |
            |She sets down the lamp.
            |
            |STEEL
            |(quietly)
            |So much for retirement.
            |
            |CUT TO:
            |
            |EXT. POOL - NIGHT
            |
            """.trimMargin()
    }
}
