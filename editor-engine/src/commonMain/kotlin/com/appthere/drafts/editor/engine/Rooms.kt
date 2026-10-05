package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan

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

/**
 * The rooms of one document, each keeping its identity from one edit to the next.
 *
 * The rooms themselves are [roomsIn]'s, read from the text after every edit. What this adds is that a
 * room the reader is in is the *same* room afterwards -- its field is not destroyed under them -- and
 * one room the text alone does not imply: the line Enter opened under a character's name.
 */
internal class Rooms(
    private val ids: BlockIds,
) {
    var blocks: List<EditorBlock> = emptyList()
        private set

    /** The line [open] made, while it is still an empty line nobody has typed into. */
    private var opened: BlockId? = null

    /** The room an insertion at [range] is typed into, whose identity the new paragraph takes. */
    fun at(range: SourceSpan): BlockId? =
        blocks.firstOrNull { range.length == 0 && it.block.source?.start == range.start }?.id

    /** Every room in [text], all of them new: for a text that has been read again from the start. */
    fun reset(
        text: String,
        parsed: List<Block>,
    ) {
        opened = null
        blocks = found(text, parsed, previous = emptyList(), inheriting = null)
    }

    /** The rooms after [range] was replaced, [delta] longer. [inheriting] has become a paragraph. */
    fun edited(
        text: String,
        parsed: List<Block>,
        range: SourceSpan,
        delta: Int,
        inheriting: BlockId?,
    ) {
        blocks = found(text, parsed, blocks.shiftedPast(range, delta), inheriting)
    }

    /**
     * A room on the empty line at [at], laid out as [role] -- where Enter after a character's name
     * puts the caret (`BlockParser.enterAt`).
     *
     * [roomsIn] finds room only in blank lines, and the line under a name must not be one: a blank
     * line there ends the speech before it has begun. So this room is opened rather than found, and
     * lasts while its line stays empty; the first letter typed into it makes it the dialogue.
     */
    fun open(
        at: Int,
        role: BlockRole,
        text: String,
        parsed: List<Block>,
    ) = place(EditorBlock(ids.next(), roomAt(at, role)), text, parsed)

    /**
     * [emptied] -- a block whose every character was just deleted -- as the room left where it was.
     *
     * The reverse of typing into a room. The reader deleted the words, not the paragraph, and the
     * caret is still in it: with no block of that identity left, the field closed under them, and a
     * document of one paragraph had nowhere left to type. Usually the text leaves a room there and
     * this gives it the block's identity; under a character's name it leaves none, and this opens one
     * as Enter would.
     */
    fun keep(
        emptied: EditorBlock,
        at: Int,
        text: String,
        parsed: List<Block>,
    ) = place(EditorBlock(emptied.id, roomAt(at, emptied.block.role)), text, parsed)

    /** [room], in place of any room already at its offset, for as long as the text has room for it. */
    private fun place(
        room: EditorBlock,
        text: String,
        parsed: List<Block>,
    ) {
        opened = room.id
        blocks = found(text, parsed, blocks.filter { it.offset() != room.offset() } + room, inheriting = null)
    }

    /**
     * The rooms in [text] as it now stands. [previous] are the rooms from before, already moved by
     * the edit; one at the same offset is the same room.
     */
    private fun found(
        text: String,
        parsed: List<Block>,
        previous: List<EditorBlock>,
        inheriting: BlockId?,
    ): List<EditorBlock> {
        val kept = previous.filter { it.id != inheriting }
        val byOffset = kept.associateBy { it.offset() }
        val offsets = roomsIn(text, parsed)
        val rooms = offsets.map { at -> byOffset[at] ?: EditorBlock(ids.next(), emptyRoomAt(at)) }

        val open =
            kept
                .firstOrNull { it.id == opened }
                ?.takeIf {
                    it.offset() !in offsets && isEmptyLineAt(text, it.offset()) &&
                        parsed.none { b -> b.covers(it.offset()) }
                }
        opened = open?.id

        return rooms + listOfNotNull(open)
    }
}

private fun emptyRoomAt(at: Int) = Paragraph(inlines = emptyList(), source = SourceSpan.of(at, at))

private fun roomAt(
    at: Int,
    role: BlockRole,
) = Paragraph(inlines = emptyList(), role = role, source = SourceSpan.of(at, at))

private fun EditorBlock.offset(): Int = block.source?.start?.value ?: 0

/** Whether [at] is the start of a line with nothing on it. */
private fun isEmptyLineAt(
    text: String,
    at: Int,
): Boolean = at in 1..text.length && text[at - 1] == '\n' && (at == text.length || text[at] == '\n')

/** Whether [at] is inside this block rather than at either end of it, as a boneyard's blank lines are. */
private fun Block.covers(at: Int): Boolean = source?.let { at > it.start.value && at < it.endExclusive.value } ?: false

/** These rooms, moved by an edit to [range] that made the text [delta] longer. */
private fun List<EditorBlock>.shiftedPast(
    range: SourceSpan,
    delta: Int,
): List<EditorBlock> =
    map { room ->
        val at = room.offset()
        when {
            at < range.start.value -> room
            at >= range.endExclusive.value && range.length > 0 -> EditorBlock(room.id, room.block.shiftedBy(delta))
            at > range.start.value -> EditorBlock(room.id, room.block.shiftedBy(delta))
            else -> room
        }
    }
