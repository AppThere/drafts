package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.CodeFence
import com.appthere.drafts.core.model.SourceSpan
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/**
 * Lowers fenced and indented code blocks.
 *
 * The two are different constructs rather than two spellings of one. Re-emitting an indented block
 * as fenced changes which characters are significant inside it, and a block containing a backtick
 * run cannot be fenced with a shorter one -- so the fence, or its absence, is recorded rather than
 * normalised.
 */
internal class CodeLowering(
    private val source: String,
) {
    /** ``` ```kotlin … ``` ``` */
    fun fenced(node: ASTNode): Block {
        val fenceStart =
            node.children
                .firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_START }
                ?.text()
                .orEmpty()

        val content =
            node.children
                .filter { it.type in CONTENT_TOKENS }
                .joinToString("") { it.text() }

        return CodeBlock(
            text = content.removePrefix("\n"),
            language =
                node.children
                    .firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
                    ?.text()
                    ?.trim(),
            fence = fenceOf(fenceStart),
            source = node.span(),
        )
    }

    /** Four spaces of indent, no fence. `fence = null` is what records that. */
    fun indented(node: ASTNode): Block =
        CodeBlock(
            text = node.text().lines().joinToString("\n") { it.removePrefix(INDENT) },
            fence = null,
            source = node.span(),
        )

    private fun fenceOf(fenceStart: String): CodeFence {
        val char = fenceStart.firstOrNull() ?: CodeFence.BACKTICK
        val length = fenceStart.takeWhile { it == char }.length
        return CodeFence(char, length.coerceAtLeast(CodeFence.MIN_LENGTH))
    }

    private fun ASTNode.text(): String = getTextInNode(source).toString()

    private fun ASTNode.span(): SourceSpan = SourceSpan.of(startOffset, endOffset)

    private companion object {
        const val INDENT = "    "

        /** The newlines count: they are part of the code, not separators between blocks. */
        val CONTENT_TOKENS =
            setOf(MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.EOL)
    }
}
