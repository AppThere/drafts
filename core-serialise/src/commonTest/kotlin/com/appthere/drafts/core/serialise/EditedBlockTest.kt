package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockIndex
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.parse.markdown.MarkdownDocumentParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 1's third acceptance criterion: "editing one block and re-serialising leaves every other
 * byte untouched."
 *
 * The most important tests in this module. A round-trip of an unedited document can pass while the
 * serialiser is quietly wrong, because it never has to make a decision -- every block has bytes to
 * copy. These force one block onto the canonical path while its neighbours stay on the source
 * path, which is the mixed state every real save is in.
 *
 * Note the distinction these tests exist to pin down: an **edited** block keeps its span, because
 * it still occupies that region of the file, and is listed in `rewritten`. A block with no span is
 * an **insertion** -- it was never in the file and replaces nothing.
 */
class EditedBlockTest {
    private val parser = MarkdownDocumentParser()
    private val serialiser = MarkdownSerialiser()

    @Test
    fun `replacing one paragraph leaves the others byte-identical`() {
        val source =
            """
            # Title

            First paragraph with _emphasis_ that must not be normalised.

            Second paragraph, the one being replaced.

            * a star bullet
            * that must stay a star bullet
            """.trimIndent() + "\n"

        val document = parser.parse(source)
        val target = document.blocks.indexOfFirst { it.isParagraphContaining("replaced") }

        val edited =
            document.copy(
                blocks =
                    document.blocks.mapIndexed { index, block ->
                        if (index == target) {
                            // Keeps the original span: it still occupies that region of the file.
                            Paragraph(inlines = listOf(Text("Replacement text.")), source = block.source)
                        } else {
                            block
                        }
                    },
            )

        val output = serialiser.serialise(edited, source, rewritten = setOf(BlockIndex(target)))

        assertEquals(
            source.replace("Second paragraph, the one being replaced.", "Replacement text."),
            output,
        )
    }

    @Test
    fun `an edit does not normalise its neighbours`() {
        // The specific failure this guards: a serialiser that falls back to canonical style for the
        // whole document as soon as any block is rewritten. The underscore emphasis and star
        // bullets either side of the edit are the tell.
        val source = "_first_\n\nmiddle\n\n* star\n"

        val document = parser.parse(source)
        val target = document.blocks.indexOfFirst { it.isParagraphContaining("middle") }

        val edited =
            document.copy(
                blocks =
                    document.blocks.mapIndexed { index, block ->
                        if (index == target) {
                            Paragraph(inlines = listOf(Text("edited")), source = block.source)
                        } else {
                            block
                        }
                    },
            )

        val output = serialiser.serialise(edited, source, rewritten = setOf(BlockIndex(target)))

        assertTrue("_first_" in output, "Untouched emphasis was normalised: $output")
        assertTrue("* star" in output, "Untouched bullet was normalised: $output")
        assertTrue("edited" in output, "The edit did not land: $output")
    }

    @Test
    fun `a rewritten block that matches the source produces the same file`() {
        // The canonical writer and the source agree for simple content, so rewriting a plain
        // paragraph changes nothing. If this fails, the canonical path has drifted.
        val source = "plain paragraph\n"

        val document = parser.parse(source)

        assertEquals(
            source,
            serialiser.serialise(document, source, rewritten = setOf(BlockIndex(0))),
        )
    }

    @Test
    fun `a block with no span is inserted rather than replacing anything`() {
        // An insertion has no region of its own, so nothing in the original may be consumed by it.
        val source = "first\n\nsecond\n"

        val document = parser.parse(source)
        val withInsertion =
            document.copy(
                blocks = document.blocks + Paragraph(inlines = listOf(Text("appended"))),
            )

        val output = serialiser.serialise(withInsertion, source)

        assertTrue("first" in output, "Original content was consumed: $output")
        assertTrue("second" in output, "Original content was consumed: $output")
        assertTrue("appended" in output, "The insertion did not land: $output")
    }

    private fun Block.isParagraphContaining(fragment: String): Boolean =
        this is Paragraph && inlines.any { it is Text && fragment in it.value }
}
