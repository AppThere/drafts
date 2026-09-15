package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.LineBreak
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.LinkForm
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Text

/**
 * Writes [Inline] content back to Markdown.
 *
 * Every spelling choice comes from the node rather than from a house style: the delimiter the
 * author used, the backtick run they needed, the link form they wrote. That is what the extra
 * fields on the IR are for, and it is why an edited paragraph comes back looking like the rest of
 * the document instead of like the serialiser's preferences.
 *
 * A node that was synthesised rather than parsed carries the IR's defaults -- `*` emphasis, one
 * backtick, inline links -- which are exactly the canonical style the round-trip contract
 * prescribes for new content. The two rules turn out to be the same rule.
 */
internal class InlineWriter {
    fun write(inlines: List<Inline>): String = inlines.joinToString("") { write(it) }

    private fun write(inline: Inline): String =
        when (inline) {
            is Text -> inline.value
            is Emphasis -> emphasis(inline)
            is Strikethrough -> strikethrough(inline)
            is CodeSpan -> codeSpan(inline)
            is Link -> link(inline)
            is Image -> image(inline)
            is LineBreak -> if (inline.hard) HARD_BREAK else "\n"
            is RawInline -> inline.text
            else -> ""
        }

    private fun emphasis(node: Emphasis): String {
        val run =
            node.delimiter.char
                .toString()
                .repeat(if (node.strong) 2 else 1)
        return run + write(node.children) + run
    }

    private fun strikethrough(node: Strikethrough): String {
        val run = "~".repeat(node.tildeCount)
        return run + write(node.children) + run
    }

    private fun codeSpan(node: CodeSpan): String {
        val fence = "`".repeat(node.backtickCount)
        return fence + node.text + fence
    }

    private fun link(node: Link): String {
        val text = write(node.children)
        return when (val form = node.form) {
            is LinkForm.Inline -> "[$text](${node.href}${titleSuffix(node.title)})"
            is LinkForm.Reference -> "[$text][${form.label}]"
            is LinkForm.Collapsed -> "[$text][]"
            is LinkForm.Shortcut -> "[${form.label}]"
            is LinkForm.Autolink -> if (form.linkified) node.href else "<${node.href}>"
        }
    }

    private fun image(node: Image): String = "![${node.alt}](${node.src}${titleSuffix(node.title)})"

    private fun titleSuffix(title: String?): String = title?.let { """ "$it"""" }.orEmpty()

    private companion object {
        /** Two trailing spaces. The backslash form is equally valid; this one is the default. */
        const val HARD_BREAK = "  \n"
    }
}
