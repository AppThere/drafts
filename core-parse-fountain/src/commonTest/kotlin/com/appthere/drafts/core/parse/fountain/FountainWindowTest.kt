package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.BlockRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parsing one window of a screenplay, as the editor does after each edit.
 *
 * The editor never reparses a whole document for a keystroke; it hands the parser the blocks around
 * the edit. Fountain is positional, so a window parsed as if it were a file of its own goes wrong in
 * two ways: a title page found in the middle of a script, and a speech broken at the window's edge.
 * The window is read in its context instead, and these hold it to the answer a whole parse gives.
 */
class FountainWindowTest {
    private val parser = FountainDocumentParser()

    @Test
    fun `a window of blocks reads the same as those blocks in the whole parse`() {
        // Every run of whole blocks, so no edge -- after a scene heading, inside a speech, after a
        // boneyard -- is the one that happens to work. Widened first, as the editor widens it: a
        // run of blocks can end between a character and the line under it.
        val whole = parser.parse(SCRIPT).blocks

        for (first in whole.indices) {
            for (last in first until whole.size) {
                val span =
                    parser.windowAround(
                        SCRIPT,
                        whole[first].source!!.start.value,
                        whole[last].source!!.endExclusive.value,
                    )
                val inside =
                    whole.filter {
                        it.source!!.start.value >= span.start.value &&
                            it.source!!.endExclusive.value <= span.endExclusive.value
                    }
                val above = whole.lastOrNull { it.source!!.endExclusive.value <= span.start.value }?.role

                val window = parser.parseWindow(SCRIPT, span.start.value, span.endExclusive.value, above)

                assertEquals(
                    inside.map {
                        it.role to it.source
                    },
                    window.map { it.role to it.source },
                    "Blocks $first..$last read differently in a window",
                )
            }
        }
    }

    @Test
    fun `a window is widened so it never cuts a character from the line under it`() {
        val source = "A kettle sings.\n\nSTEEL\nSo much for retirement.\n"
        val character = source.indexOf("STEEL")

        val span = parser.windowAround(source, character, character + "STEEL".length)

        assertEquals("STEEL\nSo much for retirement.", source.substring(span.start.value, span.endExclusive.value))
    }

    @Test
    fun `a window in the middle of a script finds no title page`() {
        // Shaped like a title page -- "Key: value" lines -- and in the middle of the script, where
        // "Must be the first thing in the file" says it cannot be one.
        val source = "INT. OFFICE - DAY\n\nTitle: The memo on the desk\nAuthor: unknown\n"
        val from = source.indexOf("Title")

        val window = parser.parseWindow(source, from, source.length, previous = BlockRole.SCENE_HEADING)

        assertTrue(window.none { it.role == BlockRole.BODY }, "A title page was found mid-script: $window")
    }

    @Test
    fun `a speech continues into a window that starts after a blank line of spaces`() {
        val source = "STEEL\nSo much for retirement.\n \nAnd yet here we are.\n"
        val from = source.indexOf("And yet")

        val window = parser.parseWindow(source, from, source.length, previous = BlockRole.DIALOGUE)

        assertEquals(listOf(BlockRole.DIALOGUE), window.map { it.role })
    }

    private companion object {
        val SCRIPT =
            """
            Title: The Window
            Author: A. Writer

            INT. KITCHEN - NIGHT

            A kettle starts to sing.

            STEEL
            (quietly)
            So much for retirement.

            And yet here we are.

            /* A scene the writer is not sure of.

            It stays in the file. */

            JONES
            Here we are.

            CUT TO:

            .FLASHBACK

            > THE END <
            """.trimIndent() + "\n"
    }
}
