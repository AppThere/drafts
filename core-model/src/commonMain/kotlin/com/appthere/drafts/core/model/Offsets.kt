package com.appthere.drafts.core.model

import kotlin.jvm.JvmInline

/*
 * Positions in a document, as three distinct types.
 *
 * `engineering-conventions.md` 4.2 calls this out by name: block indices, character offsets
 * within a block, and absolute document offsets are three different things with the same
 * underlying `Int`, and "mixing them produces bugs that survive review". They are value classes,
 * so mixing them is a compile error and costs nothing at runtime.
 *
 * **Unit: UTF-16 code units**, throughout. That is what `intellij-markdown` reports for AST node
 * ranges and what Compose's text field reports for selection, so an offset can pass between the
 * parser and the editor untouched. `export-pipeline.md` once called `source` a "byte range"; it
 * has since been corrected, because a UTF-8 byte offset would need converting at both boundaries,
 * on a path that runs on every keystroke.
 *
 * A consequence worth stating: an astral-plane character (an emoji, most CJK extension B) is two
 * code units. Nothing here should ever index into the middle of a surrogate pair, and the parser
 * and Compose both agree on that boundary, which is precisely why this unit was chosen.
 */

/** An absolute position in the whole document's source text. */
@JvmInline
value class DocumentOffset(
    val value: Int,
) : Comparable<DocumentOffset> {
    init {
        require(value >= 0) { "DocumentOffset must not be negative, was $value" }
    }

    override fun compareTo(other: DocumentOffset): Int = value.compareTo(other.value)

    operator fun plus(codeUnits: Int): DocumentOffset = DocumentOffset(value + codeUnits)

    operator fun minus(codeUnits: Int): DocumentOffset = DocumentOffset(value - codeUnits)

    /** The distance from [other] to this offset, in code units. Negative if this comes first. */
    operator fun minus(other: DocumentOffset): Int = value - other.value

    override fun toString(): String = "doc@$value"
}

/** A position within a single block's text, measured from the start of that block. */
@JvmInline
value class BlockOffset(
    val value: Int,
) : Comparable<BlockOffset> {
    init {
        require(value >= 0) { "BlockOffset must not be negative, was $value" }
    }

    override fun compareTo(other: BlockOffset): Int = value.compareTo(other.value)

    operator fun plus(codeUnits: Int): BlockOffset = BlockOffset(value + codeUnits)

    operator fun minus(codeUnits: Int): BlockOffset = BlockOffset(value - codeUnits)

    override fun toString(): String = "block+$value"
}

/** The position of a block in its parent's list of blocks. Not an offset into any text. */
@JvmInline
value class BlockIndex(
    val value: Int,
) : Comparable<BlockIndex> {
    init {
        require(value >= 0) { "BlockIndex must not be negative, was $value" }
    }

    override fun compareTo(other: BlockIndex): Int = value.compareTo(other.value)

    operator fun plus(blocks: Int): BlockIndex = BlockIndex(value + blocks)

    operator fun minus(blocks: Int): BlockIndex = BlockIndex(value - blocks)

    override fun toString(): String = "block#$value"
}

/**
 * A half-open range of the source text: `start` is included, `endExclusive` is not.
 *
 * Every node that came from source carries one. That is round-trip contract item 1 in
 * `markdown-dialect.md` -- "retain source spans for every node; re-emit the original bytes for
 * any subtree the user did not edit" -- and it is the mechanism by which an untouched paragraph
 * survives a save completely unchanged, including whatever unusual spacing its author used.
 *
 * Half-open rather than the `IntRange` of the spec sketch, because half-open ranges compose:
 * adjacent spans satisfy `a.endExclusive == b.start`, and an empty span is representable. An
 * inclusive `IntRange` can express neither without special cases.
 */
data class SourceSpan(
    val start: DocumentOffset,
    val endExclusive: DocumentOffset,
) {
    init {
        require(start <= endExclusive) {
            "SourceSpan start ($start) must not be after endExclusive ($endExclusive)"
        }
    }

    /** Length in UTF-16 code units. */
    val length: Int get() = endExclusive - start

    val isEmpty: Boolean get() = start == endExclusive

    operator fun contains(offset: DocumentOffset): Boolean = offset >= start && offset < endExclusive

    /**
     * True when the two spans share at least one code unit.
     *
     * Used to decide which blocks a dirty range touches, so that an edit reparses those blocks and
     * no others. Empty spans overlap nothing, including themselves -- an edit of zero length
     * affects no existing text.
     */
    fun overlaps(other: SourceSpan): Boolean =
        !isEmpty && !other.isEmpty && start < other.endExclusive && other.start < endExclusive

    /** True when [other] lies entirely within this span. */
    fun covers(other: SourceSpan): Boolean = other.start >= start && other.endExclusive <= endExclusive

    /**
     * The same span, moved by [codeUnits].
     *
     * An edit shifts every span after it by the difference between what was removed and what was
     * inserted. Doing that is much cheaper than reparsing, and it is what keeps a reparse bounded
     * to the edited region rather than the document.
     */
    fun shift(codeUnits: Int): SourceSpan = SourceSpan(start + codeUnits, endExclusive + codeUnits)

    override fun toString(): String = "[${start.value},${endExclusive.value})"

    companion object {
        fun of(
            start: Int,
            endExclusive: Int,
        ): SourceSpan = SourceSpan(DocumentOffset(start), DocumentOffset(endExclusive))
    }
}
