package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.SourceSpan

/**
 * What one edit cost, so the gate can be asserted rather than observed.
 *
 * Phase 2's gate criterion is "reparse range is provably bounded -- asserted in a test, not
 * observed", which needs the engine to *report* what it reparsed. A measurement nobody can read is
 * a claim, not a result.
 */
data class EditOutcome(
    /** The span of the new text that was handed back to the parser. */
    val reparsed: SourceSpan,
    /** How many blocks were replaced. */
    val blocksReplaced: Int,
    /** How many blocks kept their identity, and so their editor state. */
    val blocksReused: Int,
    /** How many blocks after the edit had their spans moved. */
    val blocksShifted: Int,
)

/**
 * An open document: the text, the blocks, and what happens between them when someone types.
 *
 * The architecture under test (`appthere-drafts.md` 4.3): a `LazyColumn` of blocks, one editable
 * field at a time, block boundaries doubling as reparse boundaries. This class is the engine half
 * of that, and the half that decides whether the whole approach is viable -- if a keystroke cannot
 * be made to reparse a bounded amount of text, nothing above it can be fast either.
 *
 * Not a god object, and deliberately so. `engineering-conventions.md` 4.2 predicts exactly what
 * would go wrong: "`DocumentSession` will attract everything: parsing, undo, selection, autosave,
 * file access. Keep it a coordinator that owns collaborators." Undo, selection and persistence are
 * separate concerns and are not here.
 */
class DocumentSession(
    initialText: String,
    parser: BlockParser = BlockParser.Markdown(),
) {
    /** How this document is read: Markdown or Fountain, and changeable while it is untitled (7.4). */
    private var parser: BlockParser = parser

    private val ids = BlockIds()

    var text: String = initialText
        private set

    /** The blocks the parser found, which are what a reparse window is made of. */
    private var parsed: List<EditorBlock> = parser.parse(initialText).map { EditorBlock(ids.next(), it) }

    /** The empty paragraphs in the blank lines between them; see [roomsIn]. */
    private val rooms = Rooms(ids).apply { reset(initialText, parsed.map { it.block }) }

    /**
     * Every block the editor shows, in document order: [parsed] and [rooms] together.
     *
     * Never none: a document with no text has a room at the start, which is somewhere to type.
     */
    var blocks: List<EditorBlock> = merged()
        private set

    /**
     * Replaces [range] with [replacement] and reparses as little as it can get away with.
     *
     * The window comes from 4.3: "reparse the dirty block plus enough context to catch constructs
     * that span blocks (list continuation, fenced code, footnote definitions...). A conservative
     * window of 'the enclosing container block, or the paragraph before through the paragraph
     * after' is correct and fast enough."
     *
     * Conservative is the operative word. A window one block wider than strictly necessary costs a
     * fraction of a millisecond; a window one block too narrow silently mis-parses a list that the
     * edit joined to its neighbour, and the user finds out later.
     */
    fun edit(
        range: SourceSpan,
        replacement: String,
    ): EditOutcome {
        val delta = replacement.length - range.length
        val updated = text.replaceRange(range.start.value, range.endExclusive.value, replacement)

        // Typing into a room: the paragraph the parser now finds there takes the room's identity,
        // so the field the reader is typing into is not destroyed under them after one character.
        val inheriting = rooms.at(range)

        var window = dirtyWindow(range)
        var reparseSpan = reparseSpan(window, range, replacement.length, delta, updated.length)

        // Widened until the parser can read it -- whole chunks, for Fountain -- and the window
        // takes in every block the wider span reaches.
        while (!window.isEmpty()) {
            val widened = parser.window(updated, reparseSpan)
            val grown = grownTo(window, widened, delta)
            if (grown == window && widened == reparseSpan) break
            window = grown
            reparseSpan = widened.union(reparseSpan(window, range, replacement.length, delta, updated.length))
        }

        val before = if (window.isEmpty()) emptyList() else parsed.subList(0, window.first)
        val after = if (window.isEmpty()) emptyList() else parsed.subList(window.last + 1, parsed.size)

        // Captured before `parsed` is reassigned. An edit that merges two blocks into one leaves
        // the new list shorter than the window, so reading it afterwards indexes off the end --
        // which is exactly what the list-joining test caught.
        val windowIds = window.map { parsed[it].id }.toSet()

        val replacement0 = parser.reparse(updated, reparseSpan, before.lastOrNull()?.block)
        val edited = SourceSpan.of(range.start.value, range.start.value + replacement.length)
        val reconciled = reconcile(window.map { parsed[it] }, replacement0, edited, inheriting)

        text = updated
        parsed = before + reconciled + after.map { EditorBlock(it.id, it.block.shiftedBy(delta)) }
        rooms.edited(text, parsed.map { it.block }, range, delta, inheriting)
        blocks = merged()

        return EditOutcome(
            reparsed = reparseSpan,
            blocksReplaced = reconciled.size,
            blocksReused = reconciled.count { it.id in windowIds },
            blocksShifted = after.size,
        )
    }

    /**
     * The index range of blocks to throw away and rebuild: the touched ones, plus one either side.
     *
     * The neighbours are what catch constructs the edit may have joined or split -- typing `- ` at
     * the head of a paragraph below a list makes it a list item, which cannot be seen by looking at
     * that paragraph alone.
     */
    private fun dirtyWindow(range: SourceSpan): IntRange {
        if (parsed.isEmpty()) return IntRange.EMPTY

        val touched = parsed.indices.filter { index -> parsed[index].touches(range) }

        // An edit that touches no block is in a gap -- typing into a room. Its window is the blocks
        // either side of the gap, which is where whatever it typed will join or split.
        val above = parsed.indexOfLast { it.endsAtOrBefore(range.start.value) }.takeIf { it >= 0 }
        val below = parsed.indexOfFirst { it.startsAtOrAfter(range.endExclusive.value) }.takeIf { it >= 0 }

        val first = (touched.minOrNull() ?: above ?: below ?: parsed.lastIndex) - 1
        val last = (touched.maxOrNull() ?: below ?: above ?: parsed.lastIndex) + 1

        return first.coerceAtLeast(0)..last.coerceAtMost(parsed.lastIndex)
    }

    /**
     * The span of the *new* text covered by the dirty window.
     *
     * Read from the old block spans and corrected by the edit's delta, so it needs no second pass
     * over the document. Extended to a line boundary at each end, because a window that begins
     * mid-line would hand the parser a fragment that is not a block.
     */
    private fun reparseSpan(
        window: IntRange,
        range: SourceSpan,
        inserted: Int,
        delta: Int,
        newLength: Int,
    ): SourceSpan {
        if (window.isEmpty()) return SourceSpan.of(0, newLength)

        // From the start of the document, or the end, when the window reaches it: the text beyond
        // the first or last block is blank lines, cheap to read, and where a room's new words go.
        val start =
            if (window.first == 0) {
                0
            } else {
                parsed[window.first]
                    .block.source
                    ?.start
                    ?.value ?: 0
            }
        val end =
            if (window.last == parsed.lastIndex) {
                newLength
            } else {
                (
                    parsed[window.last]
                        .block.source
                        ?.endExclusive
                        ?.value ?: text.length
                ) + delta
            }

        // Wide enough for the edit itself, which typing into a gap can put outside both blocks.
        return SourceSpan.of(
            minOf(start, range.start.value).coerceIn(0, newLength),
            maxOf(end, range.start.value + inserted).coerceIn(start.coerceIn(0, newLength), newLength),
        )
    }

    /**
     * Gives the rebuilt blocks the identities of the ones they replaced, position by position.
     *
     * Positional matching, not content matching. The common edit is a keystroke inside one
     * paragraph: its content changed, its position did not, and it must keep its id or the field
     * the user is typing into is destroyed and recreated. Matching on content would fail in exactly
     * that case, which is the one that matters.
     */
    private fun reconcile(
        old: List<EditorBlock>,
        rebuilt: List<com.appthere.drafts.core.model.Block>,
        edited: SourceSpan,
        inheriting: BlockId?,
    ): List<EditorBlock> {
        // Blocks wholly before the edit match from the front, and blocks wholly after it from the
        // back, so a split or a join does not shuffle the identities of its neighbours. What is
        // left in the middle -- the block the edit is in -- matches position by position.
        val front = rebuilt.takeWhile { it.endsAtOrBefore(edited.start.value) }.size.coerceAtMost(old.size)
        val back =
            rebuilt
                .takeLastWhile { it.startsAtOrAfter(edited.endExclusive.value) }
                .size
                .coerceAtMost(old.size - front)
                .coerceAtMost(rebuilt.size - front)

        return rebuilt.mapIndexed { index, block ->
            val fromBack = rebuilt.size - index
            val id =
                when {
                    inheriting != null && block.source?.start == edited.start -> inheriting
                    index < front -> old[index].id
                    fromBack <= back -> old[old.size - fromBack].id
                    else -> old.getOrNull(index)?.takeIf { index < old.size - back }?.id ?: ids.next()
                }
            EditorBlock(id = id, block = block)
        }
    }

    /** Whether this document is read as a screenplay, which is what decides how it is laid out (5.4). */
    val screenplay: Boolean get() = parser is BlockParser.Fountain

    /** A new session over [text], read the way this one is: what reloading from disk starts from. */
    fun freshWith(text: String): DocumentSession = DocumentSession(text, parser)

    /**
     * Reads the whole text again with [parser] -- 7.4's choice of kind while a document is
     * untitled: "choosing Fountain re-interprets the same text as Fountain". The text is unchanged,
     * so the history still applies to it; the blocks are new.
     *
     * The one place a whole document is reparsed after it has opened, and only because the reader
     * asked for exactly that.
     */
    fun reinterpretAs(parser: BlockParser) {
        this.parser = parser
        parsed = parser.parse(text).map { EditorBlock(ids.next(), it) }
        rooms.reset(text, parsed.map { it.block })
        blocks = merged()
    }

    /**
     * [window] grown to every block that [span] reaches, reading block spans in the new text's
     * offsets: those after the edit have moved by [delta].
     */
    private fun grownTo(
        window: IntRange,
        span: SourceSpan,
        delta: Int,
    ): IntRange {
        fun startOf(index: Int): Int =
            parsed[index]
                .block.source
                ?.start
                ?.value
                ?.let { if (index > window.last) it + delta else it } ?: 0

        fun endOf(index: Int): Int =
            parsed[index]
                .block.source
                ?.endExclusive
                ?.value
                ?.let { if (index > window.last) it + delta else it } ?: 0

        var first = window.first
        while (first > 0 && endOf(first - 1) > span.start.value) first--
        var last = window.last
        while (last < parsed.lastIndex && startOf(last + 1) < span.endExclusive.value) last++

        return first..last
    }

    private fun SourceSpan.union(other: SourceSpan): SourceSpan =
        SourceSpan.of(minOf(start.value, other.start.value), maxOf(endExclusive.value, other.endExclusive.value))

    /**
     * Opens an empty line at [at] for the caret, laid out as [role]: what Enter does under a
     * character's name ([Rooms.open]).
     */
    internal fun openLine(
        at: Int,
        role: BlockRole,
    ) {
        rooms.open(at, role, text, parsed.map { it.block })
        blocks = merged()
    }

    /** What Enter inserts at document offset [at], in [block]: the grammar's decision. */
    internal fun enterAt(
        at: Int,
        block: Block,
    ): Enter = parser.enterAt(text, block, at)

    /** [parsed] and [rooms] in document order. A room is never at the start of a block. */
    private fun merged(): List<EditorBlock> =
        (parsed + rooms.blocks).sortedBy {
            it.block.source
                ?.start
                ?.value ?: 0
        }

    private fun EditorBlock.endsAtOrBefore(offset: Int): Boolean = block.endsAtOrBefore(offset)

    private fun EditorBlock.startsAtOrAfter(offset: Int): Boolean = block.startsAtOrAfter(offset)

    private fun Block.endsAtOrBefore(offset: Int): Boolean = source?.let { it.endExclusive.value <= offset } ?: false

    private fun Block.startsAtOrAfter(offset: Int): Boolean = source?.let { it.start.value >= offset } ?: false

    private fun EditorBlock.touches(range: SourceSpan): Boolean {
        val span = block.source ?: return false

        // An insertion has zero length and so overlaps nothing; it still belongs to the block whose
        // extent contains it, and to the block it abuts when it lands on a boundary.
        return span.overlaps(range) ||
            range.start in span ||
            range.start.value == span.endExclusive.value ||
            range.endExclusive.value == span.start.value
    }
}

/**
 * The raw source of one block.
 *
 * Straight out of the buffer by span, which is the same mechanism byte-preserving serialisation
 * uses. A block with no span was synthesised rather than parsed and has no source to show.
 */
fun DocumentSession.sourceOf(block: Block): String {
    val span = block.source ?: return ""
    return text.substring(span.start.value, span.endExclusive.value)
}
