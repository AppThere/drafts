package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Emphasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source spans, which the whole round-trip contract rests on.
 *
 * Contract item 1: "retain source spans for every node; re-emit the original bytes for any subtree
 * the user did not edit". If a span is off by one, an untouched paragraph comes back corrupted on
 * save -- and it comes back corrupted silently, which is the part that matters.
 */
class SourceSpanTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `every block span slices back to non-empty source text`() {
        val source =
            """
            # Heading

            A paragraph with *emphasis*.

            - item one
            - item two

            ```kotlin
            val x = 1
            ```
            """.trimIndent()

        val document = parser.parse(source)

        assertTrue(document.blocks.size >= 4, "Expected several blocks, got ${document.blocks.size}")
        document.blocks.forEach { block ->
            val span = requireNotNull(block.source) { "Every parsed block must carry a span" }
            val sliced = source.substring(span.start.value, span.endExclusive.value)

            assertTrue(sliced.isNotEmpty(), "Block ${block::class.simpleName} sliced to nothing from $span")
        }
    }

    @Test
    fun `a block span slices back to exactly its own text`() {
        val source = "# Heading\n\nA paragraph.\n"
        val heading = parser.parse(source).blocks.first()

        val span = requireNotNull(heading.source)
        assertEquals("# Heading", source.substring(span.start.value, span.endExclusive.value))
    }

    @Test
    fun `an emphasis span covers its delimiters`() {
        // The span is the *concrete* extent, markers included -- that is what makes re-emitting the
        // original bytes possible. Delimiters are dropped only from the lowered children.
        val source = "a *word* b"
        val emphasis = parser.parse(source).firstInline<Emphasis>()

        val span = requireNotNull(emphasis.source)
        assertEquals("*word*", source.substring(span.start.value, span.endExclusive.value))
    }

    @Test
    fun `spans are UTF-16 code unit offsets`() {
        // An astral-plane character is two code units. If offsets were bytes or code points, this
        // slice would land mid-character or short.
        val source = "a 💡 *word*"
        val emphasis = parser.parse(source).firstInline<Emphasis>()

        val span = requireNotNull(emphasis.source)
        assertEquals("*word*", source.substring(span.start.value, span.endExclusive.value))
    }
}
