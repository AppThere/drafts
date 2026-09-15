package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Align
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Table
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Lowers a GFM table (`markdown-dialect.md` 1).
 *
 * The CST shape is worth stating, because one token type does two jobs.
 * `GFMTokenTypes.TABLE_SEPARATOR` is both the `|` between cells *and* the entire delimiter row
 * `|:--|--:|` as a single token. They are told apart by depth: the delimiter row is a direct child
 * of `TABLE`, while the pipes live inside `HEADER` and `ROW`.
 *
 * Cells hold inline content only -- 1 says so directly -- so there is no recursion into blocks.
 */
internal class TableLowering(
    private val source: String,
    private val lowerInlines: (List<ASTNode>) -> List<Inline>,
) {
    fun lower(node: ASTNode): Block {
        val header = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
        val headerCells = header?.let { cellsOf(it) }.orEmpty()

        return Table(
            header = headerCells,
            alignments = alignmentsFor(node, headerCells.size),
            rows = node.children.filter { it.type == GFMElementTypes.ROW }.map { cellsOf(it) },
            source = SourceSpan.of(node.startOffset, node.endOffset),
        )
    }

    /**
     * The cells of one row, with their padding removed.
     *
     * A `CELL` spans the whitespace either side of its content -- `| A |` gives a cell of `" A "` --
     * and GFM does not count that padding as content. Keeping it would put a leading space into
     * every cell of the IR, which is invisible in a rendering and wrong in a comparison.
     *
     * The padding is still in the source, so byte-identity is unaffected: the serialiser re-emits
     * the row from its span, not from these cells.
     */
    private fun cellsOf(row: ASTNode): List<List<Inline>> =
        row.children
            .filter { it.type == GFMTokenTypes.CELL }
            .map { lowerInlines(it.children.trimWhitespace()) }

    private fun List<ASTNode>.trimWhitespace(): List<ASTNode> =
        dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
            .dropLastWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }

    /**
     * Column alignment, read from the delimiter row's colons.
     *
     * Sized to match the header, always. `Table` requires the two to agree, and GFM says a
     * mismatch means the construct is not a table at all -- but this is an editor, and a half-typed
     * table is a normal thing to be looking at. Throwing here would surface as a crash mid-keystroke
     * rather than as a table that is not finished yet.
     */
    private fun alignmentsFor(
        node: ASTNode,
        columnCount: Int,
    ): List<Align> {
        val parsed =
            node.children
                .firstOrNull { it.type == GFMTokenTypes.TABLE_SEPARATOR }
                ?.text()
                ?.split('|')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { alignmentOf(it) }
                .orEmpty()

        return List(columnCount) { index -> parsed.getOrElse(index) { Align.NONE } }
    }

    private fun alignmentOf(rule: String): Align =
        when {
            rule.startsWith(':') && rule.endsWith(':') -> Align.CENTER
            rule.startsWith(':') -> Align.LEFT
            rule.endsWith(':') -> Align.RIGHT
            else -> Align.NONE
        }

    private fun ASTNode.text(): String = getTextInNode(source).toString()
}
