package com.appthere.drafts.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The offset types exist to make one specific bug impossible: passing a block-relative offset
 * where a document-absolute one is expected. That half is enforced by the compiler and needs no
 * test. What is tested here is the arithmetic and the span semantics, which incremental reparse
 * depends on and which are easy to get subtly wrong.
 */
class OffsetsTest {
    @Test
    fun `offsets order by position`() {
        assertTrue(DocumentOffset(3) < DocumentOffset(10))
        assertTrue(BlockOffset(10) > BlockOffset(3))
        assertEquals(DocumentOffset(7), DocumentOffset(7))
    }

    @Test
    fun `subtracting two document offsets gives a distance in code units`() {
        val start = DocumentOffset(10)
        val end = DocumentOffset(25)

        assertEquals(15, end - start)
        assertEquals(-15, start - end)
    }

    @Test
    fun `a negative offset is rejected at construction`() {
        // A negative offset means an arithmetic slip upstream. Failing here localises it to the
        // place that computed it, rather than to whatever later indexes a string with it.
        assertFailsWith<IllegalArgumentException> { DocumentOffset(-1) }
        assertFailsWith<IllegalArgumentException> { BlockOffset(-1) }
        assertFailsWith<IllegalArgumentException> { BlockIndex(-1) }
    }

    @Test
    fun `span length is measured in code units`() {
        assertEquals(5, SourceSpan.of(10, 15).length)
        assertEquals(0, SourceSpan.of(10, 10).length)
    }

    @Test
    fun `a span contains its start but not its end`() {
        val span = SourceSpan.of(10, 15)

        assertTrue(DocumentOffset(10) in span)
        assertTrue(DocumentOffset(14) in span)
        assertFalse(DocumentOffset(15) in span, "The span is half-open; its end is excluded")
        assertFalse(DocumentOffset(9) in span)
    }

    @Test
    fun `adjacent spans do not overlap`() {
        // This is the property that makes half-open ranges worth using. Two blocks that sit next
        // to each other in the source share a boundary offset and must not both claim it, or an
        // edit at that offset reparses two blocks instead of one.
        val first = SourceSpan.of(0, 10)
        val second = SourceSpan.of(10, 20)

        assertFalse(first.overlaps(second))
        assertFalse(second.overlaps(first))
    }

    @Test
    fun `overlapping spans are detected in both directions`() {
        val first = SourceSpan.of(0, 10)
        val second = SourceSpan.of(5, 20)

        assertTrue(first.overlaps(second))
        assertTrue(second.overlaps(first))
    }

    @Test
    fun `an empty span overlaps nothing`() {
        // An insertion point has zero length. It sits between characters rather than over any, so
        // it cannot be said to overlap existing text -- including itself.
        val empty = SourceSpan.of(10, 10)

        assertFalse(empty.overlaps(SourceSpan.of(0, 20)))
        assertFalse(SourceSpan.of(0, 20).overlaps(empty))
        assertFalse(empty.overlaps(empty))
    }

    @Test
    fun `shifting moves both ends and preserves length`() {
        val span = SourceSpan.of(10, 15)
        val shifted = span.shift(7)

        assertEquals(SourceSpan.of(17, 22), shifted)
        assertEquals(span.length, shifted.length, "A shift must not change how much text is covered")
    }

    @Test
    fun `shifting backwards is allowed when the result stays non-negative`() {
        // Deleting text shifts everything after it left. This is the common case on backspace.
        assertEquals(SourceSpan.of(5, 10), SourceSpan.of(10, 15).shift(-5))
    }

    @Test
    fun `a span cannot end before it starts`() {
        assertFailsWith<IllegalArgumentException> { SourceSpan.of(15, 10) }
    }
}
