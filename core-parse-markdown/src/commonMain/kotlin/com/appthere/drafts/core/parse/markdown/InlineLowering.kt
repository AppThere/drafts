package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.EmphasisDelimiter
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.LineBreak
import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Text
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Lowers inline CST nodes to [Inline].
 *
 * This is where marker filtering happens, and it happens exactly once. `export-pipeline.md` is
 * explicit about it: `intellij-markdown` produces a *concrete* syntax tree, so an `EMPH` element
 * contains the `*` characters as `EMPH` **token** children alongside the text. That is what an
 * editor wants -- every byte is accounted for and offsets are exact -- but it means the lowering
 * has to drop the markers, and it means no backend downstream ever should.
 *
 * Note the collision in the library's own naming: `MarkdownElementTypes.EMPH` is the emphasis
 * *element*, while `MarkdownTokenTypes.EMPH` is the `*` or `_` *marker* inside it. Code here
 * always says which.
 *
 * Links, images and autolinks are delegated to [LinkLowering]: they need a resolution step against
 * the document's link definitions that nothing else here does.
 */
internal class InlineLowering(
    private val source: String,
    definitions: Map<String, LinkDefinition>,
) {
    private val links = LinkLowering(source, definitions, ::lowerAll)

    fun lowerAll(nodes: List<ASTNode>): List<Inline> = coalesceText(nodes.dropBreakEols().mapNotNull { lower(it) })

    /**
     * Drops the newline that follows a hard line break.
     *
     * `foo  \nbaz` is a HARD_LINE_BREAK *and* an EOL in the CST, because the concrete tree accounts
     * for every character. Lowering both gives two line breaks where the author wrote one, which
     * renders as a `<br />` followed by a stray blank line.
     */
    private fun List<ASTNode>.dropBreakEols(): List<ASTNode> =
        filterIndexed { index, node ->
            node.type != MarkdownTokenTypes.EOL ||
                getOrNull(index - 1)?.type != MarkdownTokenTypes.HARD_LINE_BREAK
        }

    private fun lower(node: ASTNode): Inline? =
        when (node.type) {
            MarkdownElementTypes.EMPH -> emphasis(node, strong = false)
            MarkdownElementTypes.STRONG -> emphasis(node, strong = true)
            MarkdownElementTypes.CODE_SPAN -> codeSpan(node)
            MarkdownElementTypes.INLINE_LINK -> links.inlineLink(node)
            MarkdownElementTypes.FULL_REFERENCE_LINK -> links.referenceLink(node, short = false)
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> links.referenceLink(node, short = true)
            MarkdownElementTypes.AUTOLINK -> links.autolink(node)
            GFMElementTypes.STRIKETHROUGH -> strikethrough(node)
            GFMTokenTypes.GFM_AUTOLINK -> links.linkified(node)
            MarkdownElementTypes.IMAGE -> links.image(node)
            MarkdownTokenTypes.HARD_LINE_BREAK -> LineBreak(hard = true, source = node.span())
            MarkdownTokenTypes.EOL -> LineBreak(hard = false, source = node.span())
            MarkdownTokenTypes.HTML_TAG -> RawInline(node.text(), Origin.RAW_HTML, node.span())
            else -> literal(node)
        }

    /**
     * Anything with no structural meaning becomes text.
     *
     * Punctuation tokens -- `LPAREN`, `COLON`, `EXCLAMATION_MARK` and the rest -- reach here when
     * they did not turn out to be part of a link or image. Treating them as text is round-trip
     * contract item 5: "anything the parser doesn't recognise is retained as literal text rather
     * than dropped."
     */
    private fun literal(node: ASTNode): Inline? =
        node.text().takeIf { it.isNotEmpty() }?.let { Text(MarkdownText.unescape(it), node.span()) }

    private fun emphasis(
        node: ASTNode,
        strong: Boolean,
    ): Inline {
        val marker = node.children.firstOrNull { it.type == MarkdownTokenTypes.EMPH }
        val delimiter =
            if (marker?.text()?.startsWith(UNDERSCORE) == true) {
                EmphasisDelimiter.UNDERSCORE
            } else {
                EmphasisDelimiter.ASTERISK
            }

        return Emphasis(
            strong = strong,
            children = lowerAll(node.children.filterNot { it.type == MarkdownTokenTypes.EMPH }),
            delimiter = delimiter,
            source = node.span(),
        )
    }

    /**
     * `~~struck~~` or `~single~` (`markdown-dialect.md` 2).
     *
     * The tildes arrive as individual tokens, one per character, so the run length is half the
     * count. Three or more tildes are not strikethrough at all under this dialect -- the parser
     * does not produce a STRIKETHROUGH node for them -- but the count is clamped anyway, because an
     * IR invariant that throws is a crash in an editor rather than a caught mistake.
     */
    private fun strikethrough(node: ASTNode): Inline {
        val tildes = node.children.count { it.type == GFMTokenTypes.TILDE }

        return Strikethrough(
            children = lowerAll(node.children.filterNot { it.type == GFMTokenTypes.TILDE }),
            tildeCount = (tildes / 2).coerceIn(1, 2),
            source = node.span(),
        )
    }

    /**
     * `` `code` ``.
     *
     * The delimiters are the first and last children, and everything between them is content --
     * which is not the same as "every child that is not a BACKTICK token". A span written with a
     * double fence to contain a literal backtick tokenises that inner backtick as a BACKTICK too,
     * so filtering by token type would silently delete the one character the author used a longer
     * fence to protect.
     *
     * The run length comes from the opening delimiter's own text, for the same reason: counting
     * backtick tokens and halving gives 1 for that example instead of 2.
     */
    private fun codeSpan(node: ASTNode): Inline {
        val children = node.children
        val opening = children.firstOrNull()?.takeIf { it.type == MarkdownTokenTypes.BACKTICK }

        val content =
            if (children.size >= DELIMITED_MINIMUM) {
                children.subList(1, children.size - 1).joinToString("") { it.text() }
            } else {
                children.joinToString("") { it.text() }
            }

        return CodeSpan(
            text = content,
            backtickCount = opening?.text()?.length ?: 1,
            source = node.span(),
        )
    }

    /**
     * Merges neighbouring [Text] runs.
     *
     * The CST splits a plain sentence across `TEXT`, `WHITE_SPACE` and punctuation tokens, so a
     * naive lowering produces a dozen single-word nodes per paragraph. Merging keeps the IR the
     * shape a reader expects, and keeps the span contiguous so the serialiser can re-emit the
     * original bytes for the whole run.
     */
    private fun coalesceText(inlines: List<Inline>): List<Inline> =
        inlines.fold(mutableListOf()) { acc, inline ->
            val previous = acc.lastOrNull()
            if (inline is Text && previous is Text) {
                acc[acc.lastIndex] =
                    Text(previous.value + inline.value, previous.source.extendTo(inline.source))
            } else {
                acc.add(inline)
            }
            acc
        }

    private fun ASTNode.text(): String = getTextInNode(source).toString()

    private fun ASTNode.span(): SourceSpan = SourceSpan.of(startOffset, endOffset)

    private companion object {
        const val UNDERSCORE = "_"

        /** An opening delimiter, at least one child of content, and a closing delimiter. */
        const val DELIMITED_MINIMUM = 3
    }
}

/** A collected `[label]: /url "title"` definition. */
internal data class LinkDefinition(
    val label: String,
    val href: String,
    val title: String?,
)

private fun SourceSpan?.extendTo(other: SourceSpan?): SourceSpan? =
    when {
        this == null -> other
        other == null -> this
        else -> SourceSpan(start, other.endExclusive)
    }
