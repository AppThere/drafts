package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.Block

/**
 * Where a paragraph can be typed that has not been typed yet: the offsets of the empty paragraphs
 * the editor shows in the blank lines between blocks.
 *
 * `appthere-drafts.md` 4.3: "pressing Enter splits". In Markdown a split is a blank line, and a blank
 * line after the last word of a paragraph -- Enter at the end of it, which is how nearly everything
 * is written -- parses as nothing at all. With no block there, the caret had nowhere to go: it fell
 * back to the end of the paragraph above, and the next words joined it ("Line 1Line 2"). Found on a
 * Chromebook, 2026-10-03, where a second bug had been hiding it.
 *
 * So the gaps between blocks are read for room:
 *
 * - **Between two blocks**, the first blank line is the separator Markdown needs. Each further pair
 *   of line breaks is room for one paragraph, at the start of the line after the pair. Enter at the
 *   end of a paragraph adds exactly one such pair.
 * - **After the last block**, every pair of line breaks is room, because nothing follows that needs
 *   separating. A file ending in a single line break, as most do, has none.
 * - **A document with no blocks** has room at the start, which is what made an empty document
 *   "ready to type into" (7.4) before there was a general rule for it.
 *
 * A room claims no text. The document's bytes -- what 8.2 digests and the serialiser writes -- are
 * exactly what they were; a room is somewhere to put the caret.
 *
 * Linear in the gaps, which are a line or two per block: it reads the blank lines between blocks
 * and parses nothing, so it can run after every edit without reparsing the document.
 */
internal fun roomsIn(
    text: String,
    blocks: List<Block>,
): List<Int> {
    val spans = blocks.mapNotNull { it.source }
    if (spans.isEmpty()) return listOf(0)

    val rooms = mutableListOf<Int>()
    spans.zipWithNext { above, below ->
        rooms += roomBetween(text, above.endExclusive.value, below.start.value, separated = true)
    }
    rooms += roomBetween(text, spans.last().endExclusive.value, text.length, separated = false)
    return rooms
}

/**
 * The rooms in the gap from [from] to [to]. [separated] is whether a block follows, so that the
 * gap's first blank line is spoken for.
 */
private fun roomBetween(
    text: String,
    from: Int,
    to: Int,
    separated: Boolean,
): List<Int> {
    val lineStarts = (from until to).filter { text[it] == '\n' }.map { it + 1 }
    val spare = if (separated) lineStarts.size - SEPARATOR_BREAKS else lineStarts.size

    return (1..spare / BREAKS_PER_ROOM).map { room -> lineStarts[room * BREAKS_PER_ROOM - 1] }
}

/** The line breaks in the blank line Markdown needs between two blocks. */
private const val SEPARATOR_BREAKS = 2

/** The line breaks one more paragraph needs: what Enter at the end of a paragraph inserts. */
private const val BREAKS_PER_ROOM = 2
