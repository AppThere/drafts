package com.appthere.drafts.editor.ui

import androidx.compose.ui.graphics.Color
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.parse.markdown.MarkdownDocumentParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The correspondence between what the preview shows and what the file says.
 *
 * Everything that reaches out of a single block depends on this being right: a click has to land
 * the caret where it was aimed, and a selection drawn over preview glyphs has to come back as a
 * range of source to copy. Being one character out is not a rounding error here -- it is the caret
 * landing inside the emphasis markers the preview is supposed to be hiding.
 */
class PreviewMapTest {
    @Test
    fun `text with no markup maps one for one`() {
        val preview = previewOf("Plain text here.")

        assertEquals("Plain text here.", preview.text.text)
        assertEquals(6, preview.sourceOffsetAt(6))
    }

    @Test
    fun `an offset after hidden markup maps past the markers`() {
        // "A *word* here." is fourteen characters; its preview is twelve. Everything after the
        // emphasis sits two characters later in the file than it appears on screen.
        val preview = previewOf("A *word* here.")

        assertEquals("A word here.", preview.text.text)
        assertEquals(SOURCE_HERE, preview.sourceOffsetAt(PREVIEW_HERE))
    }

    @Test
    fun `the map runs both ways`() {
        val preview = previewOf("A *word* here.")

        assertEquals(PREVIEW_HERE, preview.previewOffsetAt(SOURCE_HERE))
    }

    @Test
    fun `an offset inside emphasised text maps inside the emphasised source`() {
        val preview = previewOf("A *word* here.")

        // "w" is at preview 2 and at source 3, just past the opening marker.
        assertEquals(3, preview.sourceOffsetAt(2))
    }

    @Test
    fun `a bullet has no source of its own and resolves to the text beside it`() {
        // The bullet is decoration the preview supplies; there is nothing in the file it came from.
        // Clicking it should put the caret at the start of the item, which is what a reader means.
        val preview = previewOf("- item")

        assertEquals("• item", preview.text.text)
        assertEquals(2, preview.sourceOffsetAt(0))
    }

    @Test
    fun `a fenced code block maps through its fences`() {
        val preview = previewOf("```kotlin\nfun main() {}\n```")

        assertEquals("```kotlin\nfun main() {}\n```", preview.text.text)
        assertEquals(10, preview.sourceOffsetAt(10))
    }

    @Test
    fun `the map never goes backwards`() {
        // Monotonicity matters more than exactness. A map that stepped backwards would let a
        // selection invert itself halfway through a drag, which looks like a bug in the drag.
        val preview = previewOf("A *word*, some `code`, and a [link](https://example.com).")

        val offsets =
            preview.text.text.indices
                .mapNotNull { preview.sourceOffsetAt(it) }

        assertEquals(offsets.sorted(), offsets, "The map is not monotonic")
    }

    private fun previewOf(source: String): BlockPreview = previewOfBlock(parse(source), Color.Unspecified)

    private fun parse(source: String): Block = MarkdownDocumentParser().parse(source).blocks.first()

    private companion object {
        /** Where "here." starts, on screen and in the file. */
        const val PREVIEW_HERE = 7
        const val SOURCE_HERE = 9
    }
}
