package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.FootnoteRef
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.LineBreak
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.Underline

/**
 * Writes [Inline] content back to Fountain.
 *
 * `fountain.md`'s whole inline vocabulary is four spellings:
 *
 * | Markup | Result |
 * |---|---|
 * | `*text*` | Italic |
 * | `**text**` | Bold |
 * | `***text***` | Bold italic |
 * | `_text_` | Underline |
 *
 * Which is less than the IR can hold, and the difference is deliberate rather than an omission.
 * Fountain has no link, no image, no code span and no strikethrough; a screenplay that acquired one
 * by being edited as Markdown cannot carry it back out. Those nodes are written as their text, so
 * the words survive even where the markup cannot.
 *
 * The notable asymmetry with Markdown is `_`. There it is emphasis; here it is underline, and so an
 * [Emphasis] node is written with asterisks no matter which delimiter it was parsed with. Writing
 * `_x_` for an italic would change what the document says.
 */
internal class FountainInlineWriter {
    fun write(inlines: List<Inline>): String = inlines.joinToString("") { write(it) }

    /**
     * The text of [inlines] with nothing escaped and no markup re-added.
     *
     * For the blocks whose characters are to come back exactly as typed -- a boneyard, where
     * "emphasis does not apply" -- and which therefore hold their source as one unparsed [Text].
     */
    fun raw(inlines: List<Inline>): String =
        inlines.joinToString("") { inline ->
            when (inline) {
                is Text -> inline.value
                is RawInline -> inline.text
                else -> write(inline)
            }
        }

    private fun write(inline: Inline): String =
        when (inline) {
            is Text -> escape(inline.value)

            is Emphasis -> emphasis(inline)

            is Underline -> "_" + write(inline.children) + "_"

            // A note or a boneyard opened mid-line. Already the characters it was written with, and
            // nothing inside it was parsed, so nothing inside it may be re-escaped.
            is RawInline -> inline.text

            // Fountain has no hard break: a line ending is a line ending, and in dialogue it is
            // significant on its own.
            is LineBreak -> "\n"

            // The rest have no Fountain spelling at all, so each keeps its words and loses its
            // markup. Listed one by one rather than caught by an `else`, because where the words of
            // a node live differs for every one of them -- a code span's are its text, an image's
            // are its alt -- and an `else` would silently drop whichever the IR gains next.
            is Strikethrough -> write(inline.children)

            is Link -> write(inline.children)

            is CodeSpan -> escape(inline.text)

            is Image -> escape(inline.alt)

            is FootnoteRef -> ""
        }

    /** `*`, `**`, and `***` as bold around italic, which is how the parser reads it back. */
    private fun emphasis(node: Emphasis): String {
        val run = if (node.strong) "**" else "*"

        return run + write(node.children) + run
    }

    /**
     * Puts back the backslashes the parse took out.
     *
     * [Text] holds *semantic* text: the inline pass resolved `\*` to `*`, because that is what a
     * renderer and an exporter need. Writing that straight back out would re-parse as markup -- an
     * asterisk the author escaped would become italics, and their line would change meaning on a
     * save.
     *
     * Only the canonical path reaches here. A block that still has its source span is re-emitted
     * byte for byte, so ordinary prose is untouched by this no matter how conservative it is.
     *
     * Three characters, which is the full set and not a convenient subset:
     *
     * - `\`, because it is what escapes the others.
     * - `*` and `_`, each of which can open emphasis or underline.
     *
     * Everything else in the parser's escapable set is safe unescaped once those three are handled.
     * A note needs `[[`, so a lone `[` is not markup; `]]` only ever closes a note that was opened,
     * so `]` is never markup; and a boneyard needs `/` *followed by* an asterisk, which cannot
     * happen in output where every asterisk is already escaped. Escaping them anyway would put
     * backslashes through `and/or` and every `[beat]` in the script, which is a visible cost for no
     * correctness gain.
     */
    private fun escape(value: String): String =
        buildString(value.length) {
            value.forEach { char ->
                if (char in MARKUP) append('\\')
                append(char)
            }
        }

    private companion object {
        /** The characters that begin Fountain markup, and so change meaning if written unescaped. */
        val MARKUP = charArrayOf('\\', '*', '_').toSet()
    }
}
