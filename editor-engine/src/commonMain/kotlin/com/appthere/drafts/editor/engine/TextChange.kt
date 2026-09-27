package com.appthere.drafts.editor.engine

import kotlin.math.min

/**
 * The smallest replacement that turns one string into another.
 *
 * A text field reports its whole contents after every keystroke, not the keystroke. Passing that
 * straight to the engine works -- the text ends up right -- but it records "the whole block was
 * replaced by the whole block" as the thing that happened, and that is not something undo can be
 * clever about. It is neither an insertion nor a deletion, so nothing coalesces, and undo goes back
 * one keystroke at a time no matter how carefully [UndoHistory] was written.
 *
 * Narrowing to the changed run puts the typing back in view: a keystroke becomes an insertion of one
 * character at a known offset, which is exactly what the history is looking for.
 *
 * Correct for a single contiguous change, which is what a text field ever produces from one edit.
 * Two separate changes at once would be reported as one run spanning both -- still correct text,
 * just a coarser entry than it could be.
 */
data class TextChange(
    val start: Int,
    val endExclusive: Int,
    val replacement: String,
)

/**
 * The change between [old] and [new], or null if they are identical.
 *
 * Found by matching from both ends, which is what makes an insertion in the middle come back as an
 * insertion rather than as a replacement of everything after it.
 */
fun changeBetween(
    old: String,
    new: String,
): TextChange? {
    if (old == new) return null

    val prefix = commonPrefix(old, new)
    val suffix = commonSuffix(old, new, prefix)

    return TextChange(
        start = prefix,
        endExclusive = old.length - suffix,
        replacement = new.substring(prefix, new.length - suffix),
    )
}

/**
 * How many code units the two share at the start, without splitting a surrogate pair.
 *
 * The project counts in UTF-16 code units, so a boundary can land inside an astral character -- an
 * emoji, most obviously. Cutting there would produce a replacement holding half a character, and
 * the halves would be reassembled into something else entirely.
 */
private fun commonPrefix(
    old: String,
    new: String,
): Int {
    val limit = min(old.length, new.length)
    var shared = 0
    while (shared < limit && old[shared] == new[shared]) shared++

    return if (shared > 0 && old[shared - 1].isHighSurrogate()) shared - 1 else shared
}

/** The mirror of [commonPrefix], stopping where the prefix already claimed. */
private fun commonSuffix(
    old: String,
    new: String,
    prefix: Int,
): Int {
    val limit = min(old.length, new.length) - prefix
    var shared = 0
    while (shared < limit && old[old.length - 1 - shared] == new[new.length - 1 - shared]) shared++

    return if (shared > 0 && old[old.length - shared].isLowSurrogate()) shared - 1 else shared
}
