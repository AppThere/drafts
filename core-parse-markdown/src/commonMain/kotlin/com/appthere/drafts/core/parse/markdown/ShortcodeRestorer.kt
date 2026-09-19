package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.RawPassthrough
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text

/**
 * Collapses parsed content back into opaque shortcode nodes.
 *
 * Whatever the parser made of a shortcode's innards is discarded: the nodes inside a region are
 * replaced by a single [RawInline] or [RawPassthrough] carrying the original text. That is what
 * makes the span atomic, and it means the restorer does not care whether the parser found emphasis,
 * a link, or nothing at all in there.
 *
 * Code blocks are left alone. A shortcode written inside a fence is being shown, not invoked, and
 * rewriting it would be the editor deciding it knew better than the author.
 */
internal object ShortcodeRestorer {
    fun apply(
        document: Document,
        regions: List<ShortcodeRegion>,
        source: String,
    ): Document =
        if (regions.isEmpty()) {
            document
        } else {
            document.copy(blocks = document.blocks.map { restore(it, regions, source) })
        }

    private fun restore(
        block: Block,
        regions: List<ShortcodeRegion>,
        source: String,
    ): Block =
        when (block) {
            // A shortcode alone on its line parses as a paragraph whose whole extent is the
            // shortcode. That is a block-level passthrough, not inline content.
            is Paragraph -> {
                block.wholeRegion(regions)?.asPassthrough()
                    ?: block.copy(inlines = collapse(block.inlines, regions, source))
            }

            is Heading -> {
                block.copy(inlines = collapse(block.inlines, regions, source))
            }

            is BlockQuote -> {
                block.copy(children = block.children.map { restore(it, regions, source) })
            }

            is ListBlock -> {
                block.copy(items = block.items.map { item -> item.map { restore(it, regions, source) } })
            }

            else -> {
                block
            }
        }

    /** The region this block is entirely made of, if there is one. */
    private fun Paragraph.wholeRegion(regions: List<ShortcodeRegion>): ShortcodeRegion? =
        source?.let { span -> regions.firstOrNull { it.span == span || it.covers(span) } }

    private fun ShortcodeRegion.asPassthrough(): Block = RawPassthrough(text = text, origin = origin, source = span)

    /**
     * Rewrites inline content so every shortcode becomes one opaque node.
     *
     * Two shapes, because the parser produces both. A shortcode alone between other inlines shows
     * up as nodes *inside* the region, which collapse to one. A shortcode mid-sentence usually
     * shows up the other way round -- the whole line coalesces into a single Text node that
     * *contains* the region -- and that one has to be split.
     */
    private fun collapse(
        inlines: List<Inline>,
        regions: List<ShortcodeRegion>,
        source: String,
    ): List<Inline> {
        val consumed = mutableSetOf<SourceSpan>()

        return inlines.flatMap { inline ->
            val span = inline.source
            val enclosing = span?.let { regions.firstOrNull { region -> region.covers(it) } }

            when {
                // Inside a shortcode: one opaque node for the region, however many nodes the parser
                // made of its innards.
                enclosing != null -> if (consumed.add(enclosing.span)) listOf(enclosing.raw()) else emptyList()

                inline is Text && span != null -> splitAroundShortcodes(span, regions, consumed, source)

                else -> listOf(inline)
            }
        }
    }

    /**
     * Splits a text run around the shortcodes inside it.
     *
     * The surviving pieces are cut from the **source**, not from the node's value. They were the
     * same string until literal text began carrying resolved escapes and character references; a
     * `&amp;` earlier in the paragraph now makes the value four characters shorter than its span,
     * and slicing the value by absolute offsets would cut in the wrong place.
     */
    private fun splitAroundShortcodes(
        span: SourceSpan,
        regions: List<ShortcodeRegion>,
        consumed: MutableSet<SourceSpan>,
        source: String,
    ): List<Inline> {
        val inside = regions.filter { span.covers(it.span) }
        if (inside.isEmpty()) return listOf(sliceText(source, span))

        val pieces = mutableListOf<Inline>()
        var cursor = span.start.value

        inside.forEach { region ->
            val regionStart = region.span.start.value
            if (regionStart > cursor) {
                pieces.add(sliceText(source, SourceSpan.of(cursor, regionStart)))
            }
            if (consumed.add(region.span)) {
                pieces.add(region.raw())
            }
            cursor = region.span.endExclusive.value
        }

        if (cursor < span.endExclusive.value) {
            pieces.add(sliceText(source, SourceSpan.of(cursor, span.endExclusive.value)))
        }
        return pieces
    }

    /** A text node for one span of the source, with escapes resolved as the lowering would. */
    private fun sliceText(
        source: String,
        span: SourceSpan,
    ): Text =
        Text(
            value = MarkdownText.unescape(source.substring(span.start.value, span.endExclusive.value)),
            source = span,
        )

    private fun ShortcodeRegion.raw(): Inline = RawInline(text, origin, span)
}
