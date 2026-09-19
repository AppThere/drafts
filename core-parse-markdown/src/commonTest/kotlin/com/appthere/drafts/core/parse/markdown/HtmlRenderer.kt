package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Emphasis
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
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.ThematicBreak

/**
 * Renders the IR to CommonMark's reference HTML. **Test-only, and deliberately so.**
 *
 * This exists for one job: to let the 652 specification examples assert something. The spec states
 * its expectations as HTML, and the project has no HTML backend until `:core-export-xhtml` in
 * Phase 10 -- so without this, conformance could not be measured at all during Phase 1, which is
 * precisely when the lowering is being written and is at its most wrong.
 *
 * It is not a preview of that backend and should not become one. It lives in `commonTest`, ships
 * nowhere, and follows the *spec's* output conventions rather than anything this application wants
 * to render. When Phase 10 arrives, this should be deleted rather than promoted.
 *
 * What it measures is worth being precise about. `intellij-markdown`'s own conformance is not in
 * question -- that is the library's business. What these examples exercise is **our lowering**: a
 * dropped marker, a mis-sliced span, a swallowed child. Every such bug shows up here as a diff.
 */
internal class HtmlRenderer {
    fun render(document: Document): String = document.blocks.joinToString("") { renderBlock(it) }

    private fun renderBlock(block: Block): String =
        when (block) {
            is Paragraph -> "<p>${renderInlines(block.inlines)}</p>\n"

            is Heading -> "<h${block.level}>${renderInlines(block.inlines)}</h${block.level}>\n"

            is ThematicBreak -> "<hr />\n"

            is CodeBlock -> renderCode(block)

            is BlockQuote -> "<blockquote>\n${block.children.joinToString("") { renderBlock(it) }}</blockquote>\n"

            is ListBlock -> renderList(block)

            is RawPassthrough -> block.text.ensureTrailingNewline()

            // Produces no output of its own, which is the spec's behaviour too.
            is LinkReferenceDefinition -> ""

            else -> ""
        }

    private fun renderCode(block: CodeBlock): String {
        val language = block.language?.takeIf { it.isNotBlank() }
        val attribute = language?.let { """ class="language-${escapeText(it.substringBefore(' '))}"""" }.orEmpty()

        return "<pre><code$attribute>${escapeText(block.text.ensureTrailingNewline())}</code></pre>\n"
    }

    private fun renderList(block: ListBlock): String {
        val tag = if (block.ordered) "ol" else "ul"
        val start = if (block.ordered && block.start != 1) """ start="${block.start}"""" else ""
        val items = block.items.joinToString("") { "<li>${renderItem(it, block.tight)}</li>\n" }

        return "<$tag$start>\n$items</$tag>\n"
    }

    /**
     * One list item's contents.
     *
     * A tight item drops the paragraph wrapper around its text; a loose one keeps it. That is what
     * the flag is for, and it is the whole difference between the two shapes in the spec's output.
     */
    private fun renderItem(
        item: List<Block>,
        tight: Boolean,
    ): String =
        if (tight) {
            item.mapIndexed { index, child -> renderTightChild(child, isFirst = index == 0) }.joinToString("")
        } else {
            "\n" + item.joinToString("") { renderBlock(it) }
        }

    private fun renderTightChild(
        child: Block,
        isFirst: Boolean,
    ): String =
        when {
            child is Paragraph -> renderInlines(child.inlines)

            // A nested block still begins on its own line, unless it opens the item.
            isFirst -> renderBlock(child)

            else -> "\n" + renderBlock(child)
        }

    private fun renderInlines(inlines: List<Inline>): String = inlines.joinToString("") { renderInline(it) }

    private fun renderInline(inline: Inline): String =
        when (inline) {
            is Text -> escapeText(inline.value)
            is Emphasis -> renderEmphasis(inline)
            is Strikethrough -> "<del>${renderInlines(inline.children)}</del>"
            is CodeSpan -> "<code>${escapeText(inline.text)}</code>"
            is Link -> renderLink(inline)
            is Image -> renderImage(inline)
            is LineBreak -> if (inline.hard) "<br />\n" else "\n"
            is RawInline -> inline.text
            is FootnoteRef -> """<sup class="footnote-ref">[${escapeText(inline.label)}]</sup>"""
            else -> ""
        }

    private fun renderEmphasis(inline: Emphasis): String {
        val tag = if (inline.strong) "strong" else "em"
        return "<$tag>${renderInlines(inline.children)}</$tag>"
    }

    private fun renderLink(link: Link): String {
        val title = link.title?.let { """ title="${escapeAttribute(it)}"""" }.orEmpty()
        return """<a href="${escapeAttribute(link.href)}"$title>${renderInlines(link.children)}</a>"""
    }

    private fun renderImage(image: Image): String {
        val title = image.title?.let { """ title="${escapeAttribute(it)}"""" }.orEmpty()
        return """<img src="${escapeAttribute(image.src)}" alt="${escapeAttribute(image.alt)}"$title />"""
    }

    private fun escapeText(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun escapeAttribute(value: String): String = escapeText(value).replace("\"", "&quot;")

    private fun String.ensureTrailingNewline(): String = if (endsWith("\n")) this else this + "\n"
}
