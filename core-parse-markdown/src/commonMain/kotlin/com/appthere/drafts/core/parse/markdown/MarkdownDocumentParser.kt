package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.FrontMatter
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * Markdown source in, [Document] out.
 *
 * The only public surface of this module. `intellij-markdown` types stay behind it: nothing
 * outside sees an `ASTNode`, which is what keeps `export-pipeline.md`'s contract -- "backends
 * never see Markdown or Fountain concepts; parsers never see output concepts".
 *
 * Front matter comes off first. `markdown-dialect.md` is explicit that it has to: YAML's `---`
 * delimiter is also CommonMark's thematic break, so a document that opens with metadata would
 * otherwise lose it to a horizontal rule.
 *
 * Then two passes over the tree. The first collects link reference definitions, because CommonMark
 * allows a definition to appear after the reference that uses it, so an href cannot be resolved in
 * document order. The second lowers the tree.
 *
 * Scope note: CommonMark core plus the three GFM-derived extensions the dialect inherits --
 * tables, strikethrough, and linkify with the `https` default. The four written for this project
 * (footnotes, definition lists, attribute syntax, and the shortcode/front-matter pre-pass) are
 * separate tasks; the `when` dispatch in [BlockLowering] and [InlineLowering] is where they attach.
 *
 * The GFM flavour also recognises task-list checkboxes and math, which this dialect does not
 * include. Those lower as literal text, which is round-trip contract item 5 working as intended:
 * unrecognised constructs are retained rather than dropped.
 */
class MarkdownDocumentParser {
    /**
     * Parses [source] into a [Document].
     *
     * Offsets in the result are UTF-16 code units into [source], unchanged from what the parser
     * reported -- see `:core-model`'s `Offsets.kt` for why that unit.
     */
    fun parse(source: String): Document {
        val frontMatter = FrontMatterExtractor.extract(source)
        val forParser = maskFrontMatter(source, frontMatter)

        val tree = MarkdownParser(FLAVOUR).buildMarkdownTreeFromString(forParser)
        val definitions = collectDefinitions(tree, forParser)
        val blocks = BlockLowering(forParser, InlineLowering(forParser, definitions)).lowerAll(tree.children)

        return Document(blocks = blocks, frontMatter = frontMatter)
    }

    /**
     * Blanks the front matter region so the parser cannot see it, keeping the length identical.
     *
     * Masked rather than stripped, and masked with newlines specifically. Stripping would shift
     * every offset in the document by the length of the front matter, so no span would index into
     * the original string any more -- and the source spans are the whole basis of byte-preserving
     * serialisation. Newlines are the inert choice: a run of them is blank lines, which the parser
     * discards. Spaces would not do, because four of them start an indented code block.
     *
     * The serialiser needs no knowledge of any of this. The front matter sits before the first
     * block's span, so it is copied verbatim as part of the leading gap.
     */
    private fun maskFrontMatter(
        source: String,
        frontMatter: FrontMatter?,
    ): String {
        val span = frontMatter?.source ?: return source

        return buildString(source.length) {
            repeat(span.endExclusive.value - span.start.value) { append('\n') }
            append(source, span.endExclusive.value, source.length)
        }
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
        val FLAVOUR = GFMFlavourDescriptor()
    }
}
