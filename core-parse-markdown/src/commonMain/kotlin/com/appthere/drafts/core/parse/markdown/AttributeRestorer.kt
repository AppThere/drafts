package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Attributes
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.LineBreak
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.plainText

/**
 * Attaches attribute blocks to headings and images, and generates heading ids
 * (`markdown-dialect.md` 7).
 *
 * Only those two. "Generic block attributes (`parser.attribute.block: true`) are **off** -- an
 * attribute block on an arbitrary paragraph is literal text." So a `{.foo}` after a paragraph stays
 * visible, which is what an author typing braces in prose expects.
 *
 * Heading ids are generated for every heading whether or not it carries an explicit one, because
 * duplicate suffixing is a document-wide property: a generated `intro` and a later explicit
 * `{#intro}` must not collide. Explicit ids are reserved first for the same reason, and then win
 * on merge -- 7: "An explicit `{#id}` overrides the generated one."
 */
internal object AttributeRestorer {
    fun apply(document: Document): Document {
        val ids = HeadingIds()

        // Two passes over headings. Explicit ids are claimed before any are generated, so a
        // generated slug never steals a name the author asked for later in the document.
        document.blocks.filterIsInstance<Heading>().forEach { heading ->
            explicitAttributes(heading)?.id?.let(ids::reserve)
        }

        return document.copy(blocks = document.blocks.map { restore(it, ids) })
    }

    private fun restore(
        block: Block,
        ids: HeadingIds,
    ): Block =
        when (block) {
            is Heading -> block.withAttributes(ids)
            is Paragraph -> block.withImageAttributes()
            else -> block
        }

    // --- headings --------------------------------------------------------------------------------

    /**
     * A heading's trailing `{...}`, if it has one.
     *
     * 7 requires the block to be "the last thing on the heading line", so only the final text run
     * is considered -- a brace mid-heading is prose.
     */
    private fun explicitAttributes(heading: Heading): Attributes? =
        (heading.inlines.lastOrNull() as? Text)
            ?.value
            ?.trimEnd()
            ?.let { value ->
                value
                    .lastIndexOf('{')
                    .takeIf { it >= 0 && value.endsWith('}') }
                    ?.let { open -> AttributeBlocks.parse(value.substring(open)) }
            }

    private fun Heading.withAttributes(ids: HeadingIds): Heading {
        val explicit = explicitAttributes(this)
        val stripped = if (explicit == null) inlines else inlines.withoutTrailingBlock()
        val generated = Attributes(id = ids.generate(stripped.plainText()))

        return copy(inlines = stripped, attrs = generated + (explicit ?: Attributes.EMPTY))
    }

    /** Removes the attribute block from the end of the heading's content. */
    private fun List<Inline>.withoutTrailingBlock(): List<Inline> {
        val last = lastOrNull() as? Text ?: return this
        val remainder = last.value.substringBeforeLast('{').trimEnd()

        return dropLast(1) +
            listOfNotNull(
                remainder.takeIf { it.isNotEmpty() }?.let { Text(it, last.source) },
            )
    }

    // --- images ----------------------------------------------------------------------------------

    /**
     * An attribute block on the line after a standalone image belongs to that image.
     *
     * The parser sees `![Alt](/img.png)` and the `{...}` line as one paragraph joined by a soft
     * break, because no blank line separates them -- so the shape to match is exactly
     * image, break, attribute block, and nothing else. Anything else in the paragraph means the
     * image is not standalone and 7 does not apply.
     */
    private fun Paragraph.withImageAttributes(): Paragraph {
        val image = inlines.standaloneImage()
        val attributes = inlines.trailingAttributeBlock()

        return if (image == null || attributes == null) {
            this
        } else {
            copy(inlines = listOf(image.copy(attrs = image.attrs + attributes)))
        }
    }

    /** The image, if the paragraph is exactly image, soft break, attribute block. */
    private fun List<Inline>.standaloneImage(): Image? =
        takeIf { it.size == STANDALONE_IMAGE_SIZE }
            ?.let { it[0] as? Image }
            ?.takeIf { (this[1] as? LineBreak)?.hard == false }

    private fun List<Inline>.trailingAttributeBlock(): Attributes? =
        takeIf { it.size == STANDALONE_IMAGE_SIZE }
            ?.let { (it[2] as? Text)?.value }
            ?.let(AttributeBlocks::parse)

    /** Image, soft break, attribute block. */
    private const val STANDALONE_IMAGE_SIZE = 3
}
