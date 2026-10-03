package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.EmphasisDelimiter
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.Underline

/**
 * `fountain.md`'s inline layer: emphasis, underline, notes, and boneyards opened mid-line.
 *
 * | Markup | Result |
 * |---|---|
 * | `*text*` | Italic |
 * | `**text**` | Bold |
 * | `***text***` | Bold italic |
 * | `_text_` | Underline |
 *
 * "Combinable and nestable", so this recurses; "escape with backslash", so a `\*` is an asterisk
 * and not a marker; and "asterisks or underscores surrounded by spaces on both sides are treated
 * as literal characters" -- `a * b` is literal, which is the rule that keeps prose from turning
 * into markup the moment someone multiplies two numbers.
 *
 * Notes and boneyards are opaque: they come out as passthrough spans and nothing recurses into
 * them. "Emphasis does not apply inside Boneyard or Notes."
 *
 * Every inline carries the span of its *source*, delimiters included, so the editor can map a
 * caret onto it and the serialiser can put the original characters back.
 */
internal fun inlinesIn(
    source: String,
    span: SourceSpan,
): List<Inline> = Inlines(source).parse(span.start.value, span.endExclusive.value)

private class Inlines(
    private val source: String,
) {
    fun parse(
        from: Int,
        to: Int,
    ): List<Inline> {
        val inlines = mutableListOf<Inline>()
        val words = StringBuilder()
        var wordsFrom = from
        var at = from

        fun flush(end: Int) {
            if (words.isNotEmpty()) {
                inlines += Text(words.toString(), SourceSpan.of(wordsFrom, end))
                words.clear()
            }
        }

        while (at < to) {
            val taken = take(at, to)

            if (taken == null) {
                if (words.isEmpty()) wordsFrom = at
                words.append(literalAt(at))
                at += if (escapes(at, to)) 2 else 1
            } else {
                flush(at)
                inlines += taken.inline
                at = taken.endExclusive
                wordsFrom = at
            }
        }

        flush(to)
        return inlines
    }

    /** What begins at [at], if anything does. */
    private fun take(
        at: Int,
        to: Int,
    ): Taken? =
        when {
            escapes(at, to) -> null
            source.startsWith(NOTE_OPEN, at) -> opaque(at, to, NOTE_CLOSE, Origin.FOUNTAIN_NOTE)
            source.startsWith(BONEYARD_OPEN, at) -> opaque(at, to, BONEYARD_CLOSE, Origin.FOUNTAIN_BONEYARD)
            source[at] == '*' -> emphasis(at, to)
            source[at] == '_' -> underline(at, to)
            else -> null
        }

    /**
     * A note or a boneyard, taken whole.
     *
     * An unterminated one is not markup at all: the delimiter stays in the words, which is what a
     * writer who typed `[[` and has not finished the thought is looking at while they type.
     */
    private fun opaque(
        at: Int,
        to: Int,
        close: String,
        origin: Origin,
    ): Taken? {
        val end = source.indexOf(close, at).takeIf { it in 0..<to }?.plus(close.length) ?: return null
        val span = SourceSpan.of(at, end)

        return Taken(RawInline(source.substring(at, end), origin, span), end)
    }

    /** `*`, `**` or `***`, closed by a run of the same length. */
    private fun emphasis(
        at: Int,
        to: Int,
    ): Taken? {
        val run = runLength(at, to, '*').coerceAtMost(MAX_EMPHASIS)
        val close = closerFor(at + run, to, '*', run) ?: return null
        val inside = parse(at + run, close)
        val span = SourceSpan.of(at, close + run)

        return Taken(nested(run, inside, span), close + run)
    }

    /**
     * `_text_`, which Fountain underlines where Markdown would italicise.
     *
     * A run of two or more underscores is left alone. Fountain's table has one spelling for
     * underline and no meaning for `__`, and a writer who typed two probably wants two.
     */
    private fun underline(
        at: Int,
        to: Int,
    ): Taken? =
        closerFor(at + 1, to, '_', 1)
            .takeIf { runLength(at, to, '_') == 1 }
            ?.let { close -> Taken(Underline(parse(at + 1, close), SourceSpan.of(at, close + 1)), close + 1) }

    /** Bold italic is bold around italic, which is what `***x***` means and how it nests. */
    private fun nested(
        run: Int,
        inside: List<Inline>,
        span: SourceSpan,
    ): Inline =
        when (run) {
            1 -> Emphasis(strong = false, children = inside, source = span)
            2 -> Emphasis(strong = true, children = inside, source = span)
            else -> Emphasis(true, listOf(Emphasis(false, inside, EmphasisDelimiter.ASTERISK, span)), source = span)
        }

    /**
     * Where the run opened at [from] is closed, or null if it is not.
     *
     * The flanking rules, in the form `fountain.md` states them. A marker with a space after it
     * does not open -- "asterisks or underscores surrounded by spaces on both sides are treated as
     * literal characters" -- and one with a space before it does not close. An empty pair, `**`,
     * opens nothing: there would be nothing between the markers.
     */
    private fun closerFor(
        from: Int,
        to: Int,
        marker: Char,
        run: Int,
    ): Int? {
        // A marker with a space after it does not open: "asterisks or underscores surrounded by
        // spaces on both sides are treated as literal characters".
        if (from >= to || source[from].isWhitespace()) return null

        val closer = marker.toString().repeat(run)
        var at = from
        var found: Int? = null

        while (at < to && found == null) {
            val candidate = source.indexOf(closer, at).takeIf { it in from..<to } ?: break

            found = candidate.takeIf { closes(it, to, marker, run) }
            at = candidate + run
        }

        return found
    }

    /** Whether the run at [found] closes rather than opens: no escape, and no space before it. */
    private fun closes(
        found: Int,
        to: Int,
        marker: Char,
        run: Int,
    ): Boolean =
        !escapes(found - 1, to) &&
            !source[found - 1].isWhitespace() &&
            runLength(found, to, marker) == run

    /** How many of [marker] run from [at], stopping at [to]. */
    private fun runLength(
        at: Int,
        to: Int,
        marker: Char,
    ): Int {
        var length = 0

        while (at + length < to && source[at + length] == marker) length++

        return length
    }

    /** Whether the character at [at] is a backslash escaping the one after it. */
    private fun escapes(
        at: Int,
        to: Int,
    ): Boolean = at in 0..<to - 1 && source[at] == '\\' && source[at + 1] in ESCAPABLE

    /** The character [at] contributes to the words: an escape contributes the character it escaped. */
    private fun literalAt(at: Int): Char =
        if (source[at] == '\\' &&
            at + 1 < source.length
        ) {
            source[at + 1]
        } else {
            source[at]
        }

    private data class Taken(
        val inline: Inline,
        val endExclusive: Int,
    )

    private companion object {
        const val NOTE_OPEN = "[["
        const val NOTE_CLOSE = "]]"
        const val BONEYARD_OPEN = "/" + "*"
        const val BONEYARD_CLOSE = "*" + "/"

        /** "Escape with backslash: `\*`." The markers, and the backslash itself. */
        const val ESCAPABLE = "*_[]\\/"

        const val MAX_EMPHASIS = 3
    }
}
