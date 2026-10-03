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
 * Splits [source] into chunks, from [from] onwards.
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
): List<Chunk> {
    val chunks = mutableListOf<Chunk>()
    var lines = mutableListOf<Line>()
    var at = from
    var whitespaceBefore = false

    fun finish() {
        if (lines.isNotEmpty()) {
            chunks += Chunk(lines, whitespaceBefore)
            lines = mutableListOf()
        }
    }

    while (at < source.length) {
        val lineEnd = source.indexOf('\n', at).takeIf { it >= 0 } ?: source.length
        val text = source.substring(at, lineEnd)

        if (text.isBlank()) {
            finish()
            whitespaceBefore = text.isNotEmpty()
        } else {
            lines += Line(text, at)
        }

        at = lineEnd + 1
    }

    finish()
    return chunks
}
