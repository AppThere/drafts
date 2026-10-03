package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.DefEntry
import com.appthere.drafts.core.model.DefinitionList
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.Figure
import com.appthere.drafts.core.model.FootnoteRef
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.LineBreak
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.LinkReferenceDefinition
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.RawPassthrough
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Table
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.ThematicBreak
import com.appthere.drafts.core.model.Underline

/**
 * Rewrites the spans of a nested parse so they point into the containing document.
 *
 * A footnote body is parsed on its own, dedented, so its offsets index that extracted string rather
 * than the file. Left alone they would be quietly wrong: not null, not obviously broken, just
 * pointing at the wrong characters -- which is the worst kind of wrong for something the editor
 * will use to decide what to reparse and what to leave untouched.
 *
 * Exhaustive by construction. Every node type appears here, so adding one to the IR without
 * remapping it is a compile error rather than a silent gap.
 */
internal object SpanRemapper {
    fun remap(
        blocks: List<Block>,
        body: MappedText,
    ): List<Block> = blocks.map { it.remapped(body) }

    private fun Block.remapped(body: MappedText): Block =
        when (this) {
            is Paragraph -> {
                copy(inlines = inlines.remapped(body), source = source.map(body))
            }

            is Heading -> {
                copy(inlines = inlines.remapped(body), source = source.map(body))
            }

            is BlockQuote -> {
                copy(children = children.map { it.remapped(body) }, source = source.map(body))
            }

            is CodeBlock -> {
                copy(source = source.map(body))
            }

            is ThematicBreak -> {
                copy(source = source.map(body))
            }

            is RawPassthrough -> {
                copy(source = source.map(body))
            }

            is LinkReferenceDefinition -> {
                copy(source = source.map(body))
            }

            is Figure -> {
                copy(caption = caption?.remapped(body), source = source.map(body))
            }

            is ListBlock -> {
                copy(
                    items = items.map { item -> item.map { it.remapped(body) } },
                    source = source.map(body),
                )
            }

            is DefinitionList -> {
                copy(
                    entries = entries.map { it.remapped(body) },
                    source = source.map(body),
                )
            }

            is Table -> {
                copy(
                    header = header.map { it.remapped(body) },
                    rows = rows.map { row -> row.map { it.remapped(body) } },
                    source = source.map(body),
                )
            }
        }

    private fun DefEntry.remapped(body: MappedText): DefEntry =
        copy(
            term = term.remapped(body),
            definitions = definitions.map { blocks -> blocks.map { it.remapped(body) } },
        )

    private fun List<Inline>.remapped(body: MappedText): List<Inline> = map { it.remapped(body) }

    private fun Inline.remapped(body: MappedText): Inline =
        when (this) {
            is Text -> copy(source = source.map(body))

            is CodeSpan -> copy(source = source.map(body))

            is LineBreak -> copy(source = source.map(body))

            is FootnoteRef -> copy(source = source.map(body))

            is RawInline -> copy(source = source.map(body))

            is Image -> copy(source = source.map(body))

            is Emphasis -> copy(children = children.remapped(body), source = source.map(body))

            // Markdown never produces one; the exhaustive `when` is what says so out loud.
            is Underline -> copy(children = children.remapped(body), source = source.map(body))

            is Strikethrough -> copy(children = children.remapped(body), source = source.map(body))

            is Link -> copy(children = children.remapped(body), source = source.map(body))
        }

    private fun SourceSpan?.map(body: MappedText): SourceSpan? =
        this?.let { body.spanFor(it.start.value, it.endExclusive.value) }
}
