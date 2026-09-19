package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.SourceSpan

/**
 * Text lifted out of a document, with a map back to where each character came from.
 *
 * A footnote body has to be dedented before it can be parsed -- its continuation lines are indented
 * four spaces, which CommonMark reads as a code block -- and dedenting changes lengths, so offsets
 * in the dedented text no longer index the original file. The map is what makes the spans in a
 * nested parse mean something in the document that contains it.
 */
internal class MappedText(
    val text: String,
    private val sourceOffsets: IntArray,
) {
    /** The span in the original document corresponding to [start], [endExclusive] in [text]. */
    fun spanFor(
        start: Int,
        endExclusive: Int,
    ): SourceSpan? {
        if (start >= sourceOffsets.size || start > endExclusive) return null

        val from = sourceOffsets[start]
        // A span ending at the very end of the text has no character to read an offset from, so it
        // takes one past the last mapped character.
        val to =
            if (endExclusive < sourceOffsets.size) {
                sourceOffsets[endExclusive]
            } else {
                sourceOffsets.lastOrNull()?.plus(1) ?: from
            }

        return if (to < from) null else SourceSpan.of(from, to)
    }
}

/**
 * A footnote definition, from its `[^label]:` marker to the end of its indented body.
 *
 * `markdown-dialect.md` 4: "Definition bodies may contain block content when continuation lines are
 * indented." That is what makes this more than a single line, and what makes the dedent necessary.
 */
internal data class FootnoteDefinition(
    val label: String,
    val span: SourceSpan,
    val body: MappedText,
)

/**
 * Finds footnote definitions and extracts their bodies.
 *
 * A definition runs from its marker to the last line indented under it. Blank lines are part of the
 * body when indented content follows them -- that is precisely how a multi-paragraph footnote is
 * written:
 *
 * ```
 * [^1]: First paragraph.
 *
 *     Second paragraph.
 * ```
 *
 * The body is rebuilt with the marker and four columns of indentation removed, so it parses as the
 * block sequence it is rather than as a paragraph followed by a code block. Every character keeps a
 * pointer back to where it came from.
 */
internal object FootnoteBodyScanner {
    fun findAll(source: String): List<FootnoteDefinition> =
        definitionLine.findAll(source).mapNotNull { match -> definitionAt(source, match) }.toList()

    private fun definitionAt(
        source: String,
        match: MatchResult,
    ): FootnoteDefinition? {
        val markerEnd = match.range.last + 1
        val firstEnd = firstLineEnd(source, markerEnd)
        val bodyLines = source.lineRangesFrom(markerEnd).takeWhile { it.isContinuation(source) }.toList()

        // The definition's extent runs to the end of its own line at minimum. Stopping at the
        // marker would leave the body text in the document, which is exactly what it used to do.
        val end = bodyLines.lastOrNull()?.let { it.last + 1 } ?: firstEnd

        val builder = MappedTextBuilder()
        builder.append(source, markerEnd, firstEnd)
        bodyLines.forEach { line -> builder.appendDedented(source, line) }

        return FootnoteDefinition(
            label = normaliseLinkLabel(match.groupValues[1]),
            span = SourceSpan.of(match.range.first, minOf(end, source.length)),
            body = builder.build(),
        )
    }

    private fun firstLineEnd(
        source: String,
        from: Int,
    ): Int = source.indexOf('\n', from).takeIf { it >= 0 }?.plus(1) ?: source.length

    /**
     * Line ranges after the definition's first line, each `start until endExclusive`.
     *
     * Walking lines rather than matching a single regex, because the body's extent depends on the
     * indentation of each following line and a regex that tried to express that would be worse to
     * read than the loop.
     */
    private fun String.lineRangesFrom(from: Int): Sequence<IntRange> =
        sequence {
            var start = indexOf('\n', from).takeIf { it >= 0 }?.plus(1) ?: return@sequence
            while (start < length) {
                val end = indexOf('\n', start).takeIf { it >= 0 }?.plus(1) ?: length
                yield(start until end)
                start = end
            }
        }

    /** A line belongs to the body if it is indented under the marker, or blank between such lines. */
    private fun IntRange.isContinuation(source: String): Boolean {
        val line = source.substring(first, minOf(last + 1, source.length))
        return line.isBlank() || line.startsWith(INDENT)
    }

    private const val INDENT = "    "
}

/** Builds text alongside the offsets each character came from. */
private class MappedTextBuilder {
    private val text = StringBuilder()
    private val offsets = mutableListOf<Int>()

    fun append(
        source: String,
        from: Int,
        to: Int,
    ) {
        for (index in from until minOf(to, source.length)) {
            text.append(source[index])
            offsets.add(index)
        }
    }

    /** Appends a body line with its four columns of indentation removed. */
    fun appendDedented(
        source: String,
        line: IntRange,
    ) {
        val start = line.first
        val end = minOf(line.last + 1, source.length)
        val raw = source.substring(start, end)
        val strip = if (raw.startsWith("    ")) INDENT_WIDTH else 0

        append(source, start + strip, end)
    }

    fun build() = MappedText(text.toString(), offsets.toIntArray())

    private companion object {
        const val INDENT_WIDTH = 4
    }
}

/** `[^label]:` at the start of a line, plus the whitespace after it. */
private val definitionLine = Regex("""(?m)^ {0,3}\[\^([^\]\s]+)\]:[ \t]*""")
