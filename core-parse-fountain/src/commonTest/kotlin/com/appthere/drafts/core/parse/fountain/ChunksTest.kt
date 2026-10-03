package com.appthere.drafts.core.parse.fountain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `fountain.md`: "Split the body into blocks on blank lines, retaining 'blank lines with
 * whitespace' as non-separators inside dialogue."
 *
 * The split happens here; whether a whitespace-only separator counts is decided later, where the
 * previous element is known. What this has to get right is the lines and the spans.
 */
class ChunksTest {
    @Test
    fun `lines between blank lines are one chunk`() {
        val chunks = chunksOf("INT. HOUSE - DAY\n\nShe sets down the lamp.\n")

        assertEquals(listOf(listOf("INT. HOUSE - DAY"), listOf("She sets down the lamp.")), chunks.map { it.text })
    }

    @Test
    fun `a chunk's span is the text and not the blank line after it`() {
        val source = "INT. HOUSE - DAY\n\nShe sets down the lamp.\n"
        val chunks = chunksOf(source)

        chunks.forEach { chunk ->
            val span = source.substring(chunk.source.start.value, chunk.source.endExclusive.value)
            assertEquals(chunk.text.joinToString("\n"), span)
        }
    }

    @Test
    fun `consecutive lines stay together`() {
        // "Consecutive non-blank lines form a single action block, with line breaks preserved."
        val chunks = chunksOf("She crosses the room.\nShe opens the door.\n")

        assertEquals(1, chunks.size)
        assertEquals(2, chunks.single().lines.size)
    }

    @Test
    fun `a separator of spaces is recorded as one`() {
        // The dialogue workaround. Here it is only noticed; dialogue decides what it means.
        val chunks = chunksOf("STEEL\nSo much for retirement.\n \nAnd yet.\n")

        assertEquals(2, chunks.size)
        assertFalse(chunks.first().separatedByWhitespace)
        assertTrue(chunks.last().separatedByWhitespace, "The line of spaces was read as an ordinary blank line")
    }

    @Test
    fun `an empty separator is not a whitespace one`() {
        val chunks = chunksOf("STEEL\nSo much.\n\nAction.\n")

        assertFalse(chunks.last().separatedByWhitespace)
    }

    @Test
    fun `leading blank lines do not make an empty chunk`() {
        assertEquals(1, chunksOf("\n\n\nAction.\n").size)
    }

    @Test
    fun `a document with no trailing newline keeps its last line`() {
        val chunks = chunksOf("Action.")

        assertEquals(listOf("Action."), chunks.single().text)
        assertEquals(
            0,
            chunks
                .single()
                .source.start.value,
        )
        assertEquals(
            "Action.".length,
            chunks
                .single()
                .source.endExclusive.value,
        )
    }

    @Test
    fun `an empty document has no chunks`() {
        assertTrue(chunksOf("").isEmpty())
        assertTrue(chunksOf("\n\n").isEmpty())
    }

    @Test
    fun `leading whitespace in a line is kept`() {
        // "Leading whitespace in Action is preserved -- this is the mechanism for hand-positioned
        // text."
        assertEquals(listOf("        CUT TO:"), chunksOf("        CUT TO:\n").single().text)
    }
}
