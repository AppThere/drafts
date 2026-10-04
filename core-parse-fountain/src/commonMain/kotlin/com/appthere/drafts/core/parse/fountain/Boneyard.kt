package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.SourceSpan

/**
 * The boneyard regions of [source], in order.
 *
 * `fountain.md`: "Block comments, slash-star to star-slash. Everything between is omitted from
 * output entirely, including across element boundaries." And the recommended parse order puts them
 * first: "Strip Boneyards -- they can span everything else."
 *
 * (The delimiters are spelled out in words above because Kotlin's own block comments nest, so
 * writing them literally here would open a comment inside this one.)
 *
 * Found before anything is split on blank lines, because a boneyard routinely contains one: the
 * spec's own example is a whole scene with a blank line in the middle of it. Splitting first would
 * turn one comment into two blocks with a hole between them.
 *
 * Only boneyards that **begin a line** are found here. One opened in the middle of an action line
 * is inline rather than block-level, like a note, and belongs with the inline layer -- treating it
 * as a block would cut the action in half.
 *
 * An unterminated boneyard runs to the end of the document, which is what every Fountain
 * implementation does and the only answer that does not lose the writer's text.
 *
 * Looked for between [from] and [to], so that the editor's reparse of one window reads only that
 * window. A boneyard that does not close inside it is cut at [to]: the blocks after the window are
 * left as they were, and the next whole parse -- reopening the document -- runs it to the end.
 */
internal fun boneyardsIn(
    source: String,
    from: Int = 0,
    to: Int = source.length,
): List<SourceSpan> {
    val found = mutableListOf<SourceSpan>()
    var at = from

    while (at < to) {
        val open = source.indexOf(OPEN, at).takeIf { it in 0 until to } ?: break

        // One opened mid-line is inline rather than block-level: step past it and keep looking.
        val boneyard =
            if (startsItsLine(source, open)) {
                endingAt(source, open).let { SourceSpan.of(it.start.value, minOf(it.endExclusive.value, to)) }
            } else {
                null
            }

        boneyard?.let { found += it }
        at = boneyard?.endExclusive?.value ?: (open + OPEN.length)
    }

    return found
}

/** The boneyard opening at [open], which runs to its closing delimiter or to the end. */
private fun endingAt(
    source: String,
    open: Int,
): SourceSpan {
    val close = source.indexOf(CLOSE, open + OPEN.length)

    return SourceSpan.of(open, if (close < 0) source.length else close + CLOSE.length)
}

/** Whether the opening delimiter at [open] has nothing but whitespace before it on its line. */
private fun startsItsLine(
    source: String,
    open: Int,
): Boolean {
    val lineStart = source.lastIndexOf('\n', open - 1) + 1

    return source.substring(lineStart, open).isBlank()
}

private const val OPEN = "/*"
private const val CLOSE = "*/"
