package com.appthere.drafts.editor.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Turning "here is the whole field again" back into "here is what the user did".
 *
 * Undo depends on this being narrow: a keystroke recorded as a whole-block replacement cannot be
 * coalesced with the next one, and undo degenerates to one character a press.
 */
class TextChangeTest {
    @Test
    fun `identical strings are no change at all`() {
        assertNull(changeBetween("same", "same"))
    }

    @Test
    fun `a character typed at the end is an insertion there`() {
        val change = changeBetween("Hell", "Hello")!!

        assertEquals(TextChange(4, 4, "o"), change)
    }

    @Test
    fun `a character typed in the middle is an insertion in the middle`() {
        // The case that matters: matching only from the front would report everything after the
        // caret as replaced.
        val change = changeBetween("Helo", "Hello")!!

        assertEquals("l", change.replacement)
        assertEquals(change.start, change.endExclusive)
    }

    @Test
    fun `a deleted character is a deletion`() {
        val change = changeBetween("Hello", "Hell")!!

        assertEquals(TextChange(4, 5, ""), change)
    }

    @Test
    fun `a replaced run is a replacement of just that run`() {
        val change = changeBetween("the cat sat", "the dog sat")!!

        assertEquals("dog", change.replacement)
        assertEquals(4, change.start)
        assertEquals(7, change.endExclusive)
    }

    @Test
    fun `applying the change reproduces the new text`() {
        val cases =
            listOf(
                "" to "hello",
                "hello" to "",
                "abc" to "axc",
                "abc" to "abcd",
                "abcd" to "abc",
                "aaa" to "aa",
                "the cat sat" to "the dog sat",
            )

        cases.forEach { (old, new) ->
            val change = changeBetween(old, new)
            val applied =
                change?.let { old.replaceRange(it.start, it.endExclusive, it.replacement) } ?: old

            assertEquals(new, applied, "Applying the change to \"$old\" did not give \"$new\"")
        }
    }

    @Test
    fun `an astral character is replaced whole rather than by its halves`() {
        // Offsets are UTF-16 code units, so the shared prefix of two different emoji is their
        // leading surrogate. Cutting there gives a "replacement" that is half a character: the text
        // still comes out right, but what gets recorded in the undo history is not a string anyone
        // can read, and it would be shown as one if an entry were ever described to the user.
        val change = changeBetween("a\uD83D\uDE00", "a\uD83D\uDE03")!!

        assertEquals(1, change.start, "The change began inside the surrogate pair")
        assertEquals("\uD83D\uDE03", change.replacement)
        assertEquals(
            "a\uD83D\uDE03",
            "a\uD83D\uDE00".replaceRange(change.start, change.endExclusive, change.replacement),
        )
    }

    @Test
    fun `deleting after an astral character does not disturb it`() {
        val change = changeBetween("a\uD83D\uDE00b", "a\uD83D\uDE00")!!

        assertEquals(TextChange(3, 4, ""), change)
    }
}
