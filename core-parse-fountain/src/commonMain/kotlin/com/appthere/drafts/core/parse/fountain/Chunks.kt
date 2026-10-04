package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.SourceSpan

/** One line of the source, with where it starts. */
internal data class Line(
    val text: String,
    val start: Int,
) {
    val endExclusive: Int get() = start + text.length

    val span: SourceSpan get() = SourceSpan.of(start, endExclusive)
}

/**
 * One run of lines between blank lines, with the span it came from.
 *
 * `fountain.md`: "Fountain is line-oriented and blank-line delimited. Almost every rule is of the
 * form 'an element is a line or block of lines, preceded and/or followed by a blank line, that
 * matches some shape.'" So splitting on blank lines first is not an optimisation; it is what makes
 * the rules expressible at all. The recommended parse order says the same: "Split the body into
 * blocks on blank lines."
 *
 * [separatedByWhitespace] is the one rule that breaks a naive split. "To include a blank line
 * *within* dialogue, the blank line must contain at least one space -- this is the standard
 * workaround and parsers must honor it." Whether it applies cannot be decided here, because it
 * depends on what the *previous* chunk turned out to be; so the fact is recorded and the decision
 * made where the previous element is known.
 *
 * Lines keep their offsets rather than only their text. Every block this produces carries an exact
 * span -- that is what the editor maps a caret through and what makes round-tripping free -- and
 * recomputing offsets by adding up string lengths is the kind of arithmetic that is wrong once and
 * then wrong everywhere.
 */
internal data class Chunk(
    val lines: List<Line>,
    val separatedByWhitespace: Boolean,
) {
    val source: SourceSpan get() = SourceSpan.of(lines.first().start, lines.last().endExclusive)

    val text: List<String> get() = lines.map { it.text }
}

/**
 * Splits [source] into chunks, from [from] up to [to].
 *
 * [whitespaceBefore] says whether the blank line just before [from] held spaces, which only a
 * window that starts mid-document has to be told: the first chunk of it may be more of a speech.
 *
 * A line of nothing at all ends a chunk. A line of only spaces or tabs ends one too, but says so,
 * because dialogue may want it back.
 *
 * A chunk's span covers its lines and not the blank line after them: a block's source is the text
 * it is made of, and a separator belongs to neither side of it.
 */
internal fun chunksOf(
    source: String,
    from: Int = 0,
    to: Int = source.length,
    whitespaceBefore: Boolean = false,
): List<Chunk> {
    val chunks = mutableListOf<Chunk>()
    var lines = mutableListOf<Line>()
    var at = from
    var spaced = whitespaceBefore

    fun finish() {
        if (lines.isNotEmpty()) {
            chunks += Chunk(lines, spaced)
            lines = mutableListOf()
        }
    }

    while (at < to) {
        val lineEnd = source.indexOf('\n', at).takeIf { it in 0 until to } ?: to
        val text = source.substring(at, lineEnd)

        if (text.isBlank()) {
            finish()
            spaced = text.isNotEmpty()
        } else {
            lines += Line(text, at)
        }

        at = lineEnd + 1
    }

    finish()
    return chunks
}

/**
 * [from] to [to] widened to the blank lines either side, so that no chunk is cut: from the start of
 * the first line of the chunk [from] is in, to the end of the last line of the chunk [to] is in.
 */
internal fun chunkBoundsAround(
    source: String,
    from: Int,
    to: Int,
): SourceSpan {
    var start = source.lastIndexOf('\n', (from - 1).coerceAtLeast(0)).let { if (from == 0 || it < 0) 0 else it + 1 }
    while (start > 0) {
        val previousStart = source.lastIndexOf('\n', start - 2) + 1
        if (source.substring(previousStart, start - 1).isBlank()) break
        start = previousStart
    }

    var end = source.indexOf('\n', to.coerceAtMost(source.length)).let { if (it < 0) source.length else it }
    while (end < source.length) {
        val nextEnd = source.indexOf('\n', end + 1).let { if (it < 0) source.length else it }
        if (source.substring(end + 1, nextEnd).isBlank()) break
        end = nextEnd
    }

    return SourceSpan.of(start, end)
}
