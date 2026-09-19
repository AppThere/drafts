package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.SourceSpan
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
 * Shortcodes are collapsed last, after parsing rather than before it -- see [ShortcodeScanner]
 * for why that ordering is deliberate.
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
        val footnotes = FootnoteBodyScanner.findAll(source)

        // Footnote definitions are masked along with the front matter, for the same reason: their
        // indented continuation lines would otherwise parse as code blocks in the middle of the
        // document. Masked, not removed, so every offset outside them still indexes the original.
        val forParser = maskRegions(source, listOfNotNull(frontMatter?.source) + footnotes.map { it.span })

        val tree = MarkdownParser(FLAVOUR).buildMarkdownTreeFromString(forParser)
        val definitions = collectDefinitions(tree, forParser)
        val blocks = BlockLowering(forParser, InlineLowering(forParser, definitions)).lowerAll(tree.children)

        val withShortcodes =
            ShortcodeRestorer.apply(
                Document(blocks = blocks, frontMatter = frontMatter),
                ShortcodeScanner.find(source),
                source,
            )

        // Footnotes before the structural passes: a `[^1]:` line would otherwise look like a
        // definition-list definition, both opening with a colon-ish marker on their own line.
        val withFootnotes =
            FootnoteRestorer.apply(withShortcodes, source, footnotes) { body -> parse(body).blocks }

        // Single-tilde strikethrough, which the library does not produce. Before the structural
        // passes so a struck term in a definition list is still a term.
        val withStrikethrough = StrikethroughRestorer.apply(withFootnotes, source)

        // Definition lists restructure paragraphs, attributes read the last text run of a heading.
        // Attributes go last so it sees headings in their final shape.
        return AttributeRestorer.apply(DefinitionListRestorer.apply(withStrikethrough))
    }

    /**
     * Walks the whole tree for `[label]: /url "title"`.
     *
     * Definitions nest -- one can sit inside a block quote or a list item -- so this recurses rather
     * than reading only the top level. Later definitions do not overwrite earlier ones: CommonMark
     * says the first definition of a label wins.
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
            href = MarkdownText.unescape(href.removeSurrounding("<", ">")),
            title =
                childText(MarkdownElementTypes.LINK_TITLE)
                    ?.trim('"', '\'', '(', ')')
                    ?.let(MarkdownText::unescape),
        )
    }

    /**
     * Blanks a region so the parser cannot see it, keeping the length identical.
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
    private fun maskRegions(
        source: String,
        regions: List<SourceSpan>,
    ): String {
        if (regions.isEmpty()) return source

        val masked = StringBuilder(source)
        regions.forEach { span -> masked.blank(span) }
        return masked.toString()
    }

    /**
     * Replaces a region with newlines, keeping its length.
     *
     * Newlines specifically: a run of them is blank lines, which the parser discards. Spaces would
     * not do, because four of them start an indented code block.
     *
     * `set`, not the deprecated `setCharAt` -- Kotlin/Native treats that deprecation as an error, so
     * the JVM target compiled it happily and iOS did not.
     */
    private fun StringBuilder.blank(span: SourceSpan) {
        for (index in span.start.value until minOf(span.endExclusive.value, length)) {
            if (this[index] != '\n') this[index] = '\n'
        }
    }

    private companion object {
        val FLAVOUR = GFMFlavourDescriptor()
    }
}
