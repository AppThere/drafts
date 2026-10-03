package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.FootnoteRef
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.ListMarker
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Text
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What happens to IR the Fountain syntax cannot express.
 *
 * One IR serves both formats, so a Fountain document can hold a link or a table -- not from parsing
 * Fountain, which produces neither, but from a document edited as Markdown and then written out as a
 * screenplay. `divergences.md` records what each one becomes, and these are the assertions behind
 * that record: without them it is a claim about intent rather than about the code.
 *
 * The principle throughout is that the **words** survive and the markup does not. Dropping a node
 * loses the user's text, and inventing a Fountain spelling for it produces a file no other Fountain
 * tool can read.
 */
class FountainBeyondFountainTest {
    private val serialiser = FountainSerialiser()

    @Test
    fun `inline markup Fountain has no spelling for keeps its words`() {
        assertWrites("See the appendix.", Link(href = "/appendix", children = listOf(Text("See the appendix."))))
        assertWrites("a diagram", Image(src = "/plan.png", alt = "a diagram"))
        assertWrites("val x = 1", CodeSpan("val x = 1"))
        assertWrites("struck out", Strikethrough(listOf(Text("struck out"))))
    }

    @Test
    fun `a footnote reference becomes nothing`() {
        // The only node with no words of its own: it holds a label, and its body lives in the
        // document's footnote map. There is nothing to keep.
        assertWrites("", FootnoteRef("note-1"))
    }

    @Test
    fun `a code span's text is escaped like any other text`() {
        // It stops being code the moment the backticks go, so its asterisks are now live markup.
        assertWrites("a \\* b", CodeSpan("a * b"))
    }

    @Test
    fun `a block Fountain has no spelling for is written as Markdown`() {
        // Lossy in structure and not in content. Fountain reads it back as action -- its own fallback
        // for "any paragraph that doesn't match another element" -- so the text is still there and
        // still legible, which is more than dropping it would manage.
        val document =
            Document(
                blocks =
                    listOf(
                        Paragraph(listOf(Text("She reads the list.")), BlockRole.ACTION),
                        ListBlock(
                            ordered = false,
                            items =
                                listOf(
                                    listOf(Paragraph(listOf(Text("milk")))),
                                    listOf(Paragraph(listOf(Text("eggs")))),
                                ),
                            marker = ListMarker.DASH,
                        ),
                    ),
            )

        assertEquals("She reads the list.\n\n- milk\n- eggs", serialiser.serialise(document))
    }

    private fun assertWrites(
        expected: String,
        inline: Inline,
    ) {
        val document = Document(blocks = listOf(Paragraph(listOf(inline), BlockRole.ACTION)))

        assertEquals(expected, serialiser.serialise(document), "Words were lost rather than markup")
    }
}
