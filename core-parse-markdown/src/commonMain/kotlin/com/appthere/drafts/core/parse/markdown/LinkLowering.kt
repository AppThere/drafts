package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.LinkForm
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/**
 * Lowers links, images and autolinks.
 *
 * Split out from [InlineLowering] because links carry a resolution step nothing else does: a
 * reference link's href lives in a definition that may appear anywhere in the document, so it
 * cannot be read off the node.
 *
 * @param lowerInlines lowers a link's own text, which is arbitrary inline content; supplied by
 *   [InlineLowering] so the recursion stays with the caller.
 */
internal class LinkLowering(
    private val source: String,
    private val definitions: Map<String, LinkDefinition>,
    private val lowerInlines: (List<ASTNode>) -> List<Inline>,
) {
    /** `[text](/url "title")` */
    fun inlineLink(node: ASTNode): Inline =
        Link(
            href =
                node
                    .child(MarkdownElementTypes.LINK_DESTINATION)
                    ?.text()
                    ?.stripAngles()
                    ?.let(MarkdownText::unescape)
                    .orEmpty(),
            children = linkTextChildren(node),
            title =
                node
                    .child(MarkdownElementTypes.LINK_TITLE)
                    ?.text()
                    ?.unquote()
                    ?.let(MarkdownText::unescape),
            form = LinkForm.Inline,
            source = node.span(),
        )

    /**
     * `[text][label]` and `[label]`.
     *
     * A reference with no matching definition is not an error in CommonMark -- it is literal text --
     * but the CST has already committed to a link node by this point. It lowers with an empty href
     * and keeps its form, which is what lets the serialiser write it back exactly as found.
     */
    fun referenceLink(
        node: ASTNode,
        short: Boolean,
    ): Inline {
        val label =
            node
                .child(MarkdownElementTypes.LINK_LABEL)
                ?.text()
                ?.trimBrackets()
                .orEmpty()
        val definition = definitions[normaliseLinkLabel(label)]

        // CommonMark: a reference with no definition is not a link at all, it is literal text --
        // brackets included. The CST commits to a link node before definitions are known, so the
        // decision has to be unmade here. Returning a Link with an empty href would render `[foo]`
        // as a link to nowhere, and strip the brackets the author actually typed.
        if (definition == null) return Text(node.text(), node.span())

        val form =
            when {
                short -> LinkForm.Shortcut(label)

                // `[text][]` -- an empty second bracket pair means the text doubles as the label.
                label.isEmpty() -> LinkForm.Collapsed(linkTextOf(node))

                else -> LinkForm.Reference(label)
            }

        return Link(
            href = definition.href,
            children = if (short) labelChildren(node) else linkTextChildren(node),
            title = definition.title,
            form = form,
            source = node.span(),
        )
    }

    /** `<https://example.com>` */
    fun autolink(node: ASTNode): Inline {
        val url = node.text().trim('<', '>')
        return Link(
            href = url,
            children = listOf(Text(url)),
            form = LinkForm.Autolink(linkified = false),
            source = node.span(),
        )
    }

    /**
     * A bare URL promoted to a link by linkify (`markdown-dialect.md` 3).
     *
     * This is the dialect's one deliberate divergence from GFM: a bare `www.` host gets `https`
     * prepended, following Goldmark's `linkifyProtocol` default, where GFM uses `http`. The
     * flavour descriptor has a `makeHttpsAutoLinks` flag, but it is read only by the library's own
     * HTML generator -- which this project never calls -- so the rule belongs here, where the href
     * is actually derived.
     */
    fun linkified(node: ASTNode): Inline {
        val text = node.text()
        val href = if (text.startsWith(BARE_HOST_PREFIX)) HTTPS_SCHEME + text else text

        return Link(
            href = href,
            children = listOf(Text(text)),
            form = LinkForm.Autolink(linkified = true),
            source = node.span(),
        )
    }

    /** `![alt](/src "title")` -- an IMAGE wraps an INLINE_LINK carrying the parts. */
    fun image(node: ASTNode): Inline {
        val link =
            node.child(MarkdownElementTypes.INLINE_LINK)
                ?: node.child(MarkdownElementTypes.FULL_REFERENCE_LINK)
                ?: node.child(MarkdownElementTypes.SHORT_REFERENCE_LINK)
                ?: node
        val reference = referenceTargetOf(link)

        return Image(
            src =
                link
                    .child(MarkdownElementTypes.LINK_DESTINATION)
                    ?.text()
                    ?.stripAngles()
                    ?.let(MarkdownText::unescape)
                    ?: reference?.href.orEmpty(),
            alt = altTextOf(link),
            title =
                link
                    .child(MarkdownElementTypes.LINK_TITLE)
                    ?.text()
                    ?.unquote()
                    ?.let(MarkdownText::unescape)
                    ?: reference?.title,
            source = node.span(),
        )
    }

    /** The definition an image's reference form points at, if it uses one. */
    private fun referenceTargetOf(link: ASTNode): LinkDefinition? {
        val label =
            link
                .child(MarkdownElementTypes.LINK_LABEL)
                ?.text()
                ?.trimBrackets()
                ?: link.child(MarkdownElementTypes.LINK_TEXT)?.text()?.trimBrackets()

        return label?.let { definitions[normaliseLinkLabel(it)] }
    }

    /**
     * Alt text: the rendered text of the image's content, with markup removed.
     *
     * Not the source slice. `![foo ![bar](/url)](/url2)` has alt "foo bar" -- the nested image
     * contributes its own alt -- and `![foo *bar*]` has alt "foo bar", without the asterisks.
     */
    private fun altTextOf(link: ASTNode): String {
        val content = labelChildren(link).ifEmpty { linkTextChildren(link) }
        return content.joinToString("") { it.altText() }
    }

    private fun Inline.altText(): String =
        when (this) {
            is Text -> value
            is Image -> alt
            else -> children.joinToString("") { it.altText() }
        }

    /** The label's own content, which for a shortcut reference is the link text. */
    private fun labelChildren(node: ASTNode): List<Inline> =
        node
            .child(MarkdownElementTypes.LINK_LABEL)
            ?.let { lowerInlines(it.children.filterNot { child -> child.type in BRACKETS }) }
            .orEmpty()

    private fun linkTextChildren(node: ASTNode): List<Inline> =
        node
            .child(MarkdownElementTypes.LINK_TEXT)
            ?.let { lowerInlines(it.children.filterNot { child -> child.type in BRACKETS }) }
            .orEmpty()

    private fun linkTextOf(node: ASTNode): String =
        node
            .child(MarkdownElementTypes.LINK_TEXT)
            ?.text()
            ?.trimBrackets()
            .orEmpty()

    private fun ASTNode.text(): String = getTextInNode(source).toString()

    private fun ASTNode.span(): SourceSpan = SourceSpan.of(startOffset, endOffset)

    private fun ASTNode.child(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }

    private companion object {
        const val BARE_HOST_PREFIX = "www."
        const val HTTPS_SCHEME = "https://"

        val BRACKETS = setOf(MarkdownTokenTypes.LBRACKET, MarkdownTokenTypes.RBRACKET)
    }
}

private fun String.trimBrackets(): String = removePrefix("[").removeSuffix("]")

/** `<...>` around a destination is a delimiter; `[link](<>)` has an empty href, not "<>". */
private fun String.stripAngles(): String =
    if (length >= 2 && first() == '<' && last() == '>') substring(1, length - 1) else this

private fun String.unquote(): String =
    when {
        length >= 2 && first() == '"' && last() == '"' -> substring(1, length - 1)
        length >= 2 && first() == '\'' && last() == '\'' -> substring(1, length - 1)
        length >= 2 && first() == '(' && last() == ')' -> substring(1, length - 1)
        else -> this
    }
