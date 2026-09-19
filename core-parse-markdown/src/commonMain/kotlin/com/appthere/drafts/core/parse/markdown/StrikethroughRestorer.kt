package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Text

/**
 * Turns `~single~` runs into [Strikethrough], which the library leaves as plain text.
 *
 * Double-tilde runs are already handled during lowering and never reach here: they arrive as real
 * STRIKETHROUGH nodes and the scanner deliberately ignores any run touching another tilde.
 *
 * Only text that the parser left as text is rewritten, so a tilde inside a code span or a link
 * destination is untouched -- those are not [Text] nodes by the time this runs.
 */
internal object StrikethroughRestorer {
    fun apply(
        document: Document,
        source: String,
    ): Document {
        val runs = StrikethroughScanner.find(source)

        return if (runs.isEmpty()) {
            document
        } else {
            document.copy(blocks = document.blocks.map { it.restore(runs, source) })
        }
    }

    private fun Block.restore(
        runs: List<StrikethroughRun>,
        source: String,
    ): Block =
        when (this) {
            is Paragraph -> copy(inlines = inlines.restore(runs, source))
            is Heading -> copy(inlines = inlines.restore(runs, source))
            is BlockQuote -> copy(children = children.map { it.restore(runs, source) })
            is ListBlock -> copy(items = items.map { item -> item.map { it.restore(runs, source) } })
            else -> this
        }

    private fun List<Inline>.restore(
        runs: List<StrikethroughRun>,
        source: String,
    ): List<Inline> =
        flatMap { inline ->
            val span = inline.source
            if (inline is Text && span != null) inline.split(span, runs, source) else listOf(inline)
        }

    /** Splits a text run around the strikethroughs inside it. */
    private fun Text.split(
        span: SourceSpan,
        runs: List<StrikethroughRun>,
        source: String,
    ): List<Inline> {
        val inside = runs.filter { span.covers(it.span) }
        if (inside.isEmpty()) return listOf(this)

        val pieces = mutableListOf<Inline>()
        var cursor = span.start.value

        inside.forEach { run ->
            val start = run.span.start.value
            if (start > cursor) pieces.add(sliceText(source, SourceSpan.of(cursor, start)))
            pieces.add(run.asStrikethrough(source))
            cursor = run.span.endExclusive.value
        }

        if (cursor < span.endExclusive.value) {
            pieces.add(sliceText(source, SourceSpan.of(cursor, span.endExclusive.value)))
        }
        return pieces
    }

    private fun StrikethroughRun.asStrikethrough(source: String): Strikethrough =
        Strikethrough(
            children = listOf(sliceText(source, SourceSpan.of(contentStart, contentEnd))),
            tildeCount = tildeCount,
            source = span,
        )

    /** A text node for one span of the source, with escapes resolved as the lowering would. */
    private fun sliceText(
        source: String,
        span: SourceSpan,
    ): Text =
        Text(
            value = MarkdownText.unescape(source.substring(span.start.value, span.endExclusive.value)),
            source = span,
        )
}
