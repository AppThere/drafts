package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.HeadingStyle
import com.appthere.drafts.core.model.LinkReferenceDefinition
import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.RawPassthrough
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.ThematicBreak
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes

/**
 * Lowers block-level CST nodes to [Block].
 *
 * Structural nodes only. Inline content goes to [InlineLowering]; lists and code blocks go to
 * [ListLowering] and [CodeLowering], each of which carries decisions specific to its construct.
 *
 * Nodes with no content -- the blank `EOL` and `WHITE_SPACE` tokens between blocks -- are dropped
 * here, which is safe because every surviving block keeps its exact source span, so the text
 * between spans is always recoverable from the original string.
 */
internal class BlockLowering(
    private val source: String,
    private val inlines: InlineLowering,
) {
    private val lists = ListLowering(source, ::lowerAll)
    private val tables = TableLowering(source, inlines::lowerAll)
    private val code = CodeLowering(source)

    fun lowerAll(nodes: List<ASTNode>): List<Block> = nodes.mapNotNull { lower(it) }

    private fun lower(node: ASTNode): Block? =
        when (node.type) {
            MarkdownElementTypes.PARAGRAPH -> paragraph(node)
            MarkdownElementTypes.UNORDERED_LIST -> lists.lower(node, ordered = false)
            MarkdownElementTypes.ORDERED_LIST -> lists.lower(node, ordered = true)
            MarkdownElementTypes.BLOCK_QUOTE -> blockQuote(node)
            MarkdownElementTypes.CODE_FENCE -> code.fenced(node)
            MarkdownElementTypes.CODE_BLOCK -> code.indented(node)
            MarkdownElementTypes.HTML_BLOCK -> RawPassthrough(node.text(), Origin.RAW_HTML, source = node.span())
            MarkdownElementTypes.LINK_DEFINITION -> linkDefinition(node)
            GFMElementTypes.TABLE -> tables.lower(node)
            MarkdownTokenTypes.HORIZONTAL_RULE -> ThematicBreak(source = node.span())
            in ATX_LEVELS.keys -> heading(node, ATX_LEVELS.getValue(node.type), HeadingStyle.ATX)
            in SETEXT_LEVELS.keys -> heading(node, SETEXT_LEVELS.getValue(node.type), HeadingStyle.SETEXT)
            else -> null
        }

    private fun paragraph(node: ASTNode): Block =
        Paragraph(inlines = inlines.lowerAll(node.children), source = node.span())

    /**
     * `# Heading` and `Heading` over `===`.
     *
     * The level comes from the element type rather than by counting hashes, and the marker tokens
     * are dropped. The content node differs between the two styles but nothing else does, so they
     * share this.
     */
    private fun heading(
        node: ASTNode,
        level: Int,
        style: HeadingStyle,
    ): Block {
        val contentType =
            if (style == HeadingStyle.ATX) MarkdownTokenTypes.ATX_CONTENT else MarkdownTokenTypes.SETEXT_CONTENT
        val content = node.children.firstOrNull { it.type == contentType }

        return Heading(
            level = level,
            inlines = content?.let { inlines.lowerAll(it.children.dropLeadingWhitespace()) }.orEmpty(),
            style = style,
            source = node.span(),
        )
    }

    private fun blockQuote(node: ASTNode): Block =
        BlockQuote(
            children = lowerAll(node.children.filterNot { it.type == MarkdownTokenTypes.BLOCK_QUOTE }),
            source = node.span(),
        )

    /**
     * `[label]: /url "title"`.
     *
     * Kept as a block even though it renders nothing. CommonMark removes these from the block
     * structure; an editor cannot, because dropping one on save breaks every reference-style link
     * that pointed at it.
     */
    private fun linkDefinition(node: ASTNode): Block {
        fun childText(type: IElementType): String? = node.children.firstOrNull { it.type == type }?.text()

        return LinkReferenceDefinition(
            label = childText(MarkdownElementTypes.LINK_LABEL).orEmpty().removePrefix("[").removeSuffix("]"),
            href = childText(MarkdownElementTypes.LINK_DESTINATION).orEmpty(),
            title = childText(MarkdownElementTypes.LINK_TITLE),
            source = node.span(),
        )
    }

    /**
     * Drops the whitespace between a heading's marker and its text.
     *
     * `ATX_CONTENT` spans everything after the `#` run including the space that separates them, and
     * CommonMark does not consider that space part of the heading text. Without this, every ATX
     * heading in the IR begins with a space -- invisible in most renderings and wrong in all of them.
     */
    private fun List<ASTNode>.dropLeadingWhitespace(): List<ASTNode> =
        dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }

    private fun ASTNode.text(): String = getTextInNode(source).toString()

    private fun ASTNode.span(): SourceSpan = SourceSpan.of(startOffset, endOffset)

    private companion object {
        val ATX_LEVELS: Map<IElementType, Int> =
            mapOf(
                MarkdownElementTypes.ATX_1 to 1,
                MarkdownElementTypes.ATX_2 to 2,
                MarkdownElementTypes.ATX_3 to 3,
                MarkdownElementTypes.ATX_4 to 4,
                MarkdownElementTypes.ATX_5 to 5,
                MarkdownElementTypes.ATX_6 to 6,
            )

        val SETEXT_LEVELS: Map<IElementType, Int> =
            mapOf(
                MarkdownElementTypes.SETEXT_1 to 1,
                MarkdownElementTypes.SETEXT_2 to 2,
            )
    }
}
