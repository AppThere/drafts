package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.fountain.DUAL_DIALOGUE_CLASS
import com.appthere.drafts.core.fountain.Element
import com.appthere.drafts.core.fountain.Forced
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.forcingOf
import com.appthere.drafts.core.fountain.isCharacter
import com.appthere.drafts.core.fountain.isParenthetical
import com.appthere.drafts.core.fountain.isSceneHeading
import com.appthere.drafts.core.fountain.isTransition
import com.appthere.drafts.core.fountain.sceneNumberIn
import com.appthere.drafts.core.model.Attributes
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.DocMetadata
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.HeadingStyle
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text

/**
 * Fountain 1.1, parsed into the IR every other part of this application already speaks.
 *
 * `fountain.md`: "The whole parser is a few hundred lines. Don't reach for a parser generator;
 * hand-written line matching is clearer and faster." And: "Retain source byte ranges per element
 * for editor cursor mapping and incremental reparse" -- every block this produces carries the exact
 * span it came from, which is what the block editor maps a caret through and what makes
 * `fountain.md`'s round-trip claim true: "Preserve the source verbatim for untouched regions and
 * you get perfect fidelity for free."
 *
 * The order is the one `fountain.md` recommends, for the reason it gives -- "Ambiguity resolution
 * has a natural precedence":
 *
 * 1. Boneyards, which can span everything else.
 * 2. The title page, if the file opens with one.
 * 3. Blank-line splitting into chunks.
 * 4. Each chunk: page break, forcing characters, then inference, then action as the fallback.
 *
 * Inline markup -- emphasis, notes and boneyards inside a line -- is read by [inlinesIn] as each
 * block is made.
 */
class FountainDocumentParser(
    private val keywords: FountainKeywords = FountainKeywords.ENGLISH,
) {
    fun parse(source: String): Document {
        var metadata = DocMetadata()
        val blocks = blocksIn(source, 0, source.length, previous = null) { metadata = it }

        return Document(blocks = blocks, metadata = metadata)
    }

    /**
     * The blocks between [from] and [to] of [source], in the whole document's offsets -- what the
     * editor reparses after an edit, rather than the whole screenplay.
     *
     * Read in context, because Fountain is positional: a title page only at the very start of the
     * file ("Must be the first thing in the file"), and a chunk after a blank line of spaces is more
     * of the speech before it -- which only [previous], the role of the block just above the window,
     * can say. Parsing the window as if it were a file of its own would find title pages in the
     * middle of a script and break speeches at the window's edge.
     */
    fun parseWindow(
        source: String,
        from: Int,
        to: Int,
        previous: BlockRole?,
    ): List<Block> = blocksIn(source, from, to, previous) {}

    /**
     * The smallest window holding [from] to [to] that starts and ends on a chunk boundary -- the
     * window [parseWindow] has to be given.
     *
     * A chunk is read as a whole: a character is an uppercase line *with words under it*, so a
     * window cut between the two sees an uppercase line alone, which is action. Widening to the
     * blank lines either side keeps every chunk whole.
     */
    fun windowAround(
        source: String,
        from: Int,
        to: Int,
    ): SourceSpan = chunkBoundsAround(source, from, to)

    private fun blocksIn(
        source: String,
        from: Int,
        to: Int,
        previous: BlockRole?,
        titled: (DocMetadata) -> Unit,
    ): List<Block> {
        val blocks = mutableListOf<Block>()
        var at = from
        var above = previous

        // Between each boneyard and the next: ordinary document, chunked and classified. The
        // boneyards themselves are emitted where they fall, so the blocks stay in source order and
        // nothing between them is lost.
        boneyardsIn(source, from, to).forEach { boneyard ->
            blocks += bodyIn(source, at, boneyard.start.value, above, titled)
            blocks += Paragraph(listOf(verbatim(source, boneyard)), BlockRole.NOTE, source = boneyard)
            above = BlockRole.NOTE
            at = boneyard.endExclusive.value
        }

        blocks += bodyIn(source, at, to, blocks.lastOrNull()?.role ?: above, titled)
        return blocks
    }

    /**
     * The blocks between two offsets, which is the whole document when there are no boneyards.
     *
     * [titled] is told about a title page if one is found. Only the first region can have one, and
     * only its first chunk -- "Must be the first thing in the file" -- which is why the offset is
     * what decides rather than a flag.
     */
    private fun bodyIn(
        source: String,
        from: Int,
        to: Int,
        above: BlockRole?,
        titled: (DocMetadata) -> Unit,
    ): List<Block> {
        if (from >= to) return emptyList()

        val chunks = chunksOf(source, from, to, whitespaceBefore = blankLineOfSpacesBefore(source, from))
        val blocks = mutableListOf<Block>()
        var previous: BlockRole? = above
        var first = true

        chunks.forEach { chunk ->
            if (first && from == 0 && TitlePage.opens(chunk)) {
                titled(TitlePage.metadataOf(chunk))
                blocks += Paragraph(inlinesIn(source, chunk.source), BlockRole.BODY, source = chunk.source)
            } else {
                val emitted = blocksOf(source, chunk, previous)
                blocks += emitted
                previous = emitted.lastOrNull()?.role
            }
            first = false
        }

        return blocks
    }

    /**
     * Whether the line just before [at] is blank but not empty -- the "blank line that contains at
     * least one space" that keeps a speech together across a window's edge.
     */
    private fun blankLineOfSpacesBefore(
        source: String,
        at: Int,
    ): Boolean {
        // Nothing before the start of the text, and nothing before a first line.
        val end = source.lastIndexOf('\n', at - 1).takeIf { at > 0 && it >= 0 } ?: return false
        val line = source.substring(source.lastIndexOf('\n', end - 1) + 1, end)
        return line.isNotEmpty() && line.isBlank()
    }

    /**
     * One chunk's blocks.
     *
     * [previous] is the role of the last block emitted, which decides exactly one thing: whether a
     * chunk that followed a blank-line-with-a-space is more of the same speech. "To include a blank
     * line *within* dialogue, the blank line must contain at least one space."
     */
    private fun blocksOf(
        source: String,
        chunk: Chunk,
        previous: BlockRole?,
    ): List<Block> {
        if (chunk.separatedByWhitespace && previous in speechRoles) {
            return speechIn(source, chunk.lines, previous)
        }

        val forced = forcingOf(chunk.lines.first().text)

        return forced?.let { forcedBlocks(source, chunk, it) } ?: inferredBlocks(source, chunk)
    }

    /**
     * A chunk that said nothing about itself, read by its shape.
     *
     * In `fountain.md`'s order, which is a precedence rather than a preference: a scene heading is
     * decided by a prefix, a transition needs the whole chunk to itself, a character needs someone
     * to speak under it, and action is what is left -- "any paragraph that doesn't match another
     * element".
     */
    private fun inferredBlocks(
        source: String,
        chunk: Chunk,
    ): List<Block> {
        val first = chunk.lines.first().text
        val alone = chunk.lines.size == 1

        return when {
            // "A line preceded by a blank line, followed by a blank line": with a line under it, the
            // prefix alone does not make one. It used to, and the lines under it were in no block's
            // words -- in the file, and missing from the screen.
            alone && isSceneHeading(first, keywords) -> listOf(sceneHeading(source, chunk, 0))

            alone && isTransition(first, keywords) -> listOf(paragraph(source, chunk.source, BlockRole.TRANSITION))

            // "A character line may not consist solely of uppercase if it ends in `TO:`."
            !alone && isCharacter(first) && !isTransition(first, keywords) -> characterBlocks(source, chunk.lines)

            else -> listOf(paragraph(source, chunk.source, BlockRole.ACTION))
        }
    }

    /** A chunk whose first character said what it is. */
    private fun forcedBlocks(
        source: String,
        chunk: Chunk,
        forced: Forced,
    ): List<Block> {
        val first = chunk.lines.first()

        return when (forced.element) {
            Element.CHARACTER -> {
                characterBlocks(source, chunk.lines, forced.markerLength)
            }

            Element.SECTION -> {
                listOf(
                    Heading(
                        level = forced.depth.coerceIn(1, MAX_HEADING_LEVEL),
                        inlines =
                            inlinesIn(source, SourceSpan.of(first.start + forced.markerLength, first.endExclusive)),
                        style = HeadingStyle.ATX,
                        role = BlockRole.SECTION,
                        source = chunk.source,
                    ),
                )
            }

            // "Lines prefixed with `~`. Each line is its own lyric element." Only those: a line
            // without its tilde is the action it was written as.
            Element.LYRIC -> {
                lyricsIn(source, chunk.lines)
            }

            Element.CENTERED -> {
                chunk.lines.map { line -> centred(source, line) }
            }

            Element.PAGE_BREAK -> {
                listOf(paragraph(source, chunk.source, BlockRole.PAGE_BREAK))
            }

            // Forced, a heading is one whatever follows it; what does is action, rather than words
            // that are in the file and in no block.
            Element.SCENE_HEADING -> {
                listOf(sceneHeading(source, chunk, forced.markerLength)) + actionUnder(source, chunk)
            }

            else -> {
                listOf(paragraph(source, chunk.source, forced.element.role, forced.markerLength))
            }
        }
    }

    /**
     * A scene heading, with its scene number read out of it if it has one.
     *
     * The number is "appended in `#...#` at end of line" and is archival rather than part of the
     * slugline, so it goes in the attributes and the words stop before it. The source still covers
     * the whole line, which is what puts it back.
     */
    private fun sceneHeading(
        source: String,
        chunk: Chunk,
        marker: Int,
    ): Block {
        val line = chunk.lines.first()
        val numbered = sceneNumberIn(line.text)

        // Trailing whitespace is separator rather than slugline: the space in `INT. HOUSE - DAY #1#`
        // is what holds the number off the end of the heading, and it belongs to neither. Leaving it
        // in the words was an accumulating corruption rather than a cosmetic one -- the serialiser
        // writes the number back with its own space in front, so every canonical write added one
        // more, and a heading edited three times drifted three spaces to the left of its number.
        val end =
            line.text
                .take(numbered?.second ?: line.text.length)
                .trimEnd()
                .length
        val words = SourceSpan.of(line.start + marker, line.start + end)

        return Paragraph(
            inlines = inlinesIn(source, words),
            role = BlockRole.SCENE_HEADING,
            attrs = numbered?.let { Attributes(keyValues = mapOf(SCENE_NUMBER to it.first)) } ?: Attributes.EMPTY,
            source = if (chunk.lines.size == 1) chunk.source else line.span,
        )
    }

    /** The lines of [chunk] below its first, as one block of action, or nothing if there are none. */
    private fun actionUnder(
        source: String,
        chunk: Chunk,
    ): List<Block> {
        val below = chunk.lines.drop(1).takeIf { it.isNotEmpty() } ?: return emptyList()
        val span = SourceSpan.of(below.first().start, chunk.source.endExclusive.value)
        return listOf(paragraph(source, span, BlockRole.ACTION))
    }

    /** Each `~` line of a lyric chunk a lyric, and each run of lines between them action. */
    private fun lyricsIn(
        source: String,
        lines: List<Line>,
    ): List<Block> {
        val blocks = mutableListOf<Block>()
        var plain = mutableListOf<Line>()

        fun flush() {
            if (plain.isEmpty()) return
            blocks += paragraph(source, SourceSpan.of(plain.first().start, plain.last().endExclusive), BlockRole.ACTION)
            plain = mutableListOf()
        }

        lines.forEach { line ->
            if (forcingOf(line.text)?.element == Element.LYRIC) {
                flush()
                blocks += lyric(source, line)
            } else {
                plain += line
            }
        }
        flush()
        return blocks
    }

    private companion object {
        /** Where a scene number lives, for a renderer that puts it in the margin. */
        const val SCENE_NUMBER = "scene"

        const val MAX_HEADING_LEVEL = 6
    }
}

/** The role a forced element lowers to. */
private val Element.role: BlockRole
    get() =
        when (this) {
            Element.PAGE_BREAK -> BlockRole.PAGE_BREAK
            Element.SCENE_HEADING -> BlockRole.SCENE_HEADING
            Element.ACTION -> BlockRole.ACTION
            Element.CHARACTER -> BlockRole.CHARACTER
            Element.TRANSITION -> BlockRole.TRANSITION
            Element.CENTERED -> BlockRole.CENTERED
            Element.LYRIC -> BlockRole.LYRIC
            Element.SECTION -> BlockRole.SECTION
            Element.SYNOPSIS -> BlockRole.SYNOPSIS
        }
