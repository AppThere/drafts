package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.ListMarker
import com.appthere.drafts.core.model.SourceSpan
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/**
 * Lowers `UNORDERED_LIST` and `ORDERED_LIST` to [ListBlock].
 *
 * Separate from [BlockLowering] because lists carry three decisions no other block does -- which
 * marker character, what the list starts at, and whether it is tight -- and each is recovered from
 * a different place in the tree.
 *
 * @param lowerBlocks lowers the blocks inside one list item; supplied by [BlockLowering] rather
 *   than constructed here, because item contents are arbitrary blocks and the recursion belongs to
 *   the caller.
 */
internal class ListLowering(
    private val source: String,
    private val lowerBlocks: (List<ASTNode>) -> List<Block>,
) {
    fun lower(
        node: ASTNode,
        ordered: Boolean,
    ): Block {
        val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }

        return ListBlock(
            ordered = ordered,
            items = items.map { lowerBlocks(it.children) },
            start = if (ordered) startNumberOf(items) else DEFAULT_START,
            tight = isTight(node),
            marker = markerOf(items, ordered),
            source = SourceSpan.of(node.startOffset, node.endOffset),
        )
    }

    /**
     * A list is loose when a blank line separates its items or their contents.
     *
     * CommonMark defines looseness structurally, but `intellij-markdown` does not surface it, so it
     * is recovered from the source text: a blank line inside the list's own span, other than a
     * trailing one, makes it loose. Not cosmetic -- it decides whether items render wrapped in
     * paragraphs.
     */
    private fun isTight(node: ASTNode): Boolean = !blankLine.containsMatchIn(node.text().trimEnd())

    /** `-`, `*`, `+` for bullets; `.` or `)` for ordered lists. Taken from the first item. */
    private fun markerOf(
        items: List<ASTNode>,
        ordered: Boolean,
    ): ListMarker {
        val markerType =
            if (ordered) MarkdownTokenTypes.LIST_NUMBER else MarkdownTokenTypes.LIST_BULLET
        val text = firstMarkerText(items, markerType)

        return if (ordered) {
            if (text.endsWith(")")) ListMarker.PAREN else ListMarker.PERIOD
        } else {
            when (text.firstOrNull()) {
                '*' -> ListMarker.ASTERISK
                '+' -> ListMarker.PLUS
                else -> ListMarker.DASH
            }
        }
    }

    /** `3.` starts at 3. CommonMark honours the first item's number and ignores the rest. */
    private fun startNumberOf(items: List<ASTNode>): Int =
        firstMarkerText(items, MarkdownTokenTypes.LIST_NUMBER)
            .dropLastWhile { !it.isDigit() }
            .toIntOrNull()
            ?: DEFAULT_START

    private fun firstMarkerText(
        items: List<ASTNode>,
        markerType: org.intellij.markdown.IElementType,
    ): String =
        items
            .firstNotNullOfOrNull { item -> item.children.firstOrNull { it.type == markerType } }
            ?.text()
            .orEmpty()
            .trim()

    private fun ASTNode.text(): String = getTextInNode(source).toString()

    private companion object {
        const val DEFAULT_START = 1
    }
}

private val blankLine = Regex("""\n[ \t]*\n""")
