package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Document
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * Markdown source in, [Document] out.
 *
 * The only public surface of this module. `intellij-markdown` types stay behind it: nothing
 * outside sees an `ASTNode`, which is what keeps `export-pipeline.md`'s contract -- "backends
 * never see Markdown or Fountain concepts; parsers never see output concepts".
 *
 * Two passes. The first collects link reference definitions, because CommonMark allows a
 * definition to appear after the reference that uses it, so an href cannot be resolved in document
 * order. The second lowers the tree.
 *
 * Scope note: this is the CommonMark core. The dialect's GFM-derived extensions (tables,
 * strikethrough, linkify) and the four written for this project (footnotes, definition lists,
 * attribute syntax, and the shortcode/front-matter pre-pass) are separate tasks; the `when`
 * dispatch in [BlockLowering] and [InlineLowering] is where they attach.
 */
class MarkdownDocumentParser {
    /**
     * Parses [source] into a [Document].
     *
     * Offsets in the result are UTF-16 code units into [source], unchanged from what the parser
     * reported -- see `:core-model`'s `Offsets.kt` for why that unit.
     */
    fun parse(source: String): Document {
        val tree = MarkdownParser(FLAVOUR).buildMarkdownTreeFromString(source)
        val definitions = collectDefinitions(tree, source)
        val blocks = BlockLowering(source, InlineLowering(source, definitions)).lowerAll(tree.children)

        return Document(blocks = blocks)
    }

    /**
     * Walks the whole tree for `[label]: /url "title"`.
     *
     * Definitions nest -- one can sit inside a block quote or a list item -- so this recurses
     * rather than reading only the top level. Later definitions do not overwrite earlier ones:
     * CommonMark says the first definition of a label wins.
     */
    private fun collectDefinitions(
        node: ASTNode,
        source: String,
    ): Map<String, LinkDefinition> {
        val collected = mutableMapOf<String, LinkDefinition>()

        node
            .descendants()
            .filter { it.type == MarkdownElementTypes.LINK_DEFINITION }
            .mapNotNull { definitionOf(it, source) }
            // First definition of a label wins, per CommonMark.
            .forEach { definition -> collected.getOrPut(definition.label) { definition } }

        return collected
    }

    /** This node and everything beneath it, depth-first. */
    private fun ASTNode.descendants(): Sequence<ASTNode> =
        sequenceOf(this) + children.asSequence().flatMap { it.descendants() }

    private fun definitionOf(
        node: ASTNode,
        source: String,
    ): LinkDefinition? {
        fun childText(type: IElementType): String? =
            node.children
                .firstOrNull { it.type == type }
                ?.getTextInNode(source)
                ?.toString()

        val label = childText(MarkdownElementTypes.LINK_LABEL)
        val href = childText(MarkdownElementTypes.LINK_DESTINATION)
        if (label == null || href == null) return null

        return LinkDefinition(
            label = normaliseLinkLabel(label.removePrefix("[").removeSuffix("]")),
            href = href,
            title = childText(MarkdownElementTypes.LINK_TITLE)?.trim('"', '\'', '(', ')'),
        )
    }

    private companion object {
        val FLAVOUR = CommonMarkFlavourDescriptor()
    }
}
