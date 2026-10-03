package com.appthere.drafts.editor.engine

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
 * Moves every span in a block by a fixed number of code units.
 *
 * An edit changes the length of the text before it, so every block after the edit sits at a
 * different offset even though its content is untouched. Those blocks are not reparsed -- that is
 * the whole point of a bounded reparse -- so their spans have to be corrected here instead.
 *
 * **Every span, not just the block's own.** Shifting only the outer span would leave the inline
 * spans inside pointing at the wrong characters: not null, not obviously broken, and wrong in a way
 * nothing would notice until an editor feature trusted them. The cost of doing it properly is
 * measured in `DocumentSessionTest`; it is the kind of thing a spike exists to find out.
 *
 * Exhaustive by construction, like the parser's own remapper. A new IR node type will not compile
 * until it is handled here.
 */
internal fun Block.shiftedBy(codeUnits: Int): Block = if (codeUnits == 0) this else shift(codeUnits)

private fun Block.shift(by: Int): Block =
    when (this) {
        is Paragraph -> {
            copy(inlines = inlines.shift(by), source = source.shift(by))
        }

        is Heading -> {
            copy(inlines = inlines.shift(by), source = source.shift(by))
        }

        is BlockQuote -> {
            copy(children = children.map { it.shift(by) }, source = source.shift(by))
        }

        is CodeBlock -> {
            copy(source = source.shift(by))
        }

        is ThematicBreak -> {
            copy(source = source.shift(by))
        }

        is RawPassthrough -> {
            copy(source = source.shift(by))
        }

        is LinkReferenceDefinition -> {
            copy(source = source.shift(by))
        }

        is Figure -> {
            copy(caption = caption?.shift(by), source = source.shift(by))
        }

        is ListBlock -> {
            copy(items = items.map { item -> item.map { it.shift(by) } }, source = source.shift(by))
        }

        is DefinitionList -> {
            copy(entries = entries.map { it.shift(by) }, source = source.shift(by))
        }

        is Table -> {
            copy(
                header = header.map { it.shift(by) },
                rows = rows.map { row -> row.map { it.shift(by) } },
                source = source.shift(by),
            )
        }
    }

private fun DefEntry.shift(by: Int): DefEntry =
    copy(
        term = term.shift(by),
        definitions = definitions.map { blocks -> blocks.map { it.shift(by) } },
    )

private fun List<Inline>.shift(by: Int): List<Inline> = map { it.shift(by) }

private fun Inline.shift(by: Int): Inline =
    when (this) {
        is Text -> copy(source = source.shift(by))
        is CodeSpan -> copy(source = source.shift(by))
        is LineBreak -> copy(source = source.shift(by))
        is FootnoteRef -> copy(source = source.shift(by))
        is RawInline -> copy(source = source.shift(by))
        is Image -> copy(source = source.shift(by))
        is Emphasis -> copy(children = children.shift(by), source = source.shift(by))
        is Underline -> copy(children = children.shift(by), source = source.shift(by))
        is Strikethrough -> copy(children = children.shift(by), source = source.shift(by))
        is Link -> copy(children = children.shift(by), source = source.shift(by))
    }

private fun SourceSpan?.shift(by: Int): SourceSpan? = this?.shift(by)
