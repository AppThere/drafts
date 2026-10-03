package com.appthere.drafts.core.parse.fountain

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
 * **Inline markup is not applied yet.** Each block's text is one [Text] carrying its whole span, so
 * emphasis, notes and inline boneyards read as the characters they are written with. That is the
 * next piece of this phase; the block structure, the roles and the spans -- which everything else
 * depends on -- are what is here.
 */
class FountainDocumentParser(
    private val keywords: FountainKeywords = FountainKeywords.ENGLISH,
) {
    fun parse(source: String): Document {
        val boneyards = boneyardsIn(source)
        val blocks = mutableListOf<Block>()
        var metadata = DocMetadata()
        var at = 0

        // Between each boneyard and the next: ordinary document, chunked and classified. The
        // boneyards themselves are emitted where they fall, so the blocks stay in source order and
        // nothing between them is lost.
        boneyards.forEach { boneyard ->
            blocks += bodyIn(source, at, boneyard.start.value) { metadata = it }
            blocks += Paragraph(listOf(textOf(source, boneyard)), BlockRole.NOTE, source = boneyard)
            at = boneyard.endExclusive.value
        }

        blocks += bodyIn(source, at, source.length) { metadata = it }

        return Document(blocks = blocks, metadata = metadata)
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
        titled: (DocMetadata) -> Unit,
    ): List<Block> {
        if (from >= to) return emptyList()

        val chunks = chunksOf(source.substring(0, to), from)
        val blocks = mutableListOf<Block>()
        var previous: BlockRole? = null
        var first = true

        chunks.forEach { chunk ->
            if (first && from == 0 && TitlePage.opens(chunk)) {
                titled(TitlePage.metadataOf(chunk))
                blocks += Paragraph(listOf(textOf(source, chunk.source)), BlockRole.BODY, source = chunk.source)
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
        if (chunk.separatedByWhitespace && previous in SPEECH) {
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
            isSceneHeading(first, keywords) -> listOf(sceneHeading(source, chunk, 0))
            alone && isTransition(first, keywords) -> listOf(paragraph(source, chunk.source, BlockRole.TRANSITION))
            !alone && isCharacter(first) -> characterBlocks(source, chunk.lines)
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
                            listOf(
                                textOf(source, SourceSpan.of(first.start + forced.markerLength, first.endExclusive)),
                            ),
                        style = HeadingStyle.ATX,
                        role = BlockRole.SECTION,
                        source = chunk.source,
                    ),
                )
            }

            // Each lyric line is its own element: "Each line is its own lyric element."
            Element.LYRIC -> {
                chunk.lines.map { line -> lyric(source, line) }
            }

            Element.PAGE_BREAK -> {
                listOf(paragraph(source, chunk.source, BlockRole.PAGE_BREAK))
            }

            Element.SCENE_HEADING -> {
                listOf(sceneHeading(source, chunk, forced.markerLength))
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
        val words = SourceSpan.of(line.start + marker, line.start + (numbered?.second ?: line.text.length))

        return Paragraph(
            inlines = listOf(textOf(source, words)),
            role = BlockRole.SCENE_HEADING,
            attrs = numbered?.let { Attributes(keyValues = mapOf(SCENE_NUMBER to it.first)) } ?: Attributes.EMPTY,
            source = chunk.source,
        )
    }

    /**
     * A character line and the speech under it.
     *
     * "Any text on the line immediately following a Character line or a Parenthetical" is dialogue,
     * and a line wrapped in parentheses in the same position is a parenthetical. Consecutive
     * dialogue lines are one block, which is what makes a speech one thing to edit.
     *
     * A `^` at the end of the character line is dual dialogue. The marker is dropped from the name
     * and recorded as a class, because what it decides is a two-column *layout* (5.4) rather than
     * what the element is.
     */
    private fun characterBlocks(
        source: String,
        lines: List<Line>,
        marker: Int = 0,
    ): List<Block> {
        val name = lines.first()
        val dual = name.text.trimEnd().endsWith(DUAL_MARKER)

        // "Whitespace before `^` is permitted and ignored", so the name ends at its last letter
        // rather than at the marker: `STEEL ^` is STEEL, not "STEEL ".
        val nameEnd =
            if (dual) {
                name.text
                    .trimEnd()
                    .dropLast(DUAL_MARKER.length)
                    .trimEnd()
                    .length
            } else {
                name.text.length
            }

        val blocks =
            mutableListOf<Block>(
                Paragraph(
                    inlines = listOf(textOf(source, SourceSpan.of(name.start + marker, name.start + nameEnd))),
                    role = BlockRole.CHARACTER,
                    attrs = if (dual) Attributes(classes = listOf(DUAL_CLASS)) else Attributes.EMPTY,
                    source = name.span,
                ),
            )

        blocks += speechIn(source, lines.drop(1), BlockRole.CHARACTER, dual)
        return blocks
    }

    /** The parentheticals and dialogue of one speech, from [lines]. */
    private fun speechIn(
        source: String,
        lines: List<Line>,
        previous: BlockRole?,
        dual: Boolean = false,
    ): List<Block> {
        val marks = if (dual) Attributes(classes = listOf(DUAL_CLASS)) else Attributes.EMPTY
        val blocks = mutableListOf<Block>()
        var said = mutableListOf<Line>()
        var last = previous

        fun flush() {
            if (said.isEmpty()) return

            blocks +=
                spoken(source, SourceSpan.of(said.first().start, said.last().endExclusive), BlockRole.DIALOGUE, marks)
            said = mutableListOf()
            last = BlockRole.DIALOGUE
        }

        lines.forEach { line ->
            // A parenthetical only counts where one may appear: "immediately following a Character
            // line or a Dialogue line". Anywhere else the parentheses are just parentheses.
            if (isParenthetical(line.text) && last in SPEECH) {
                flush()
                blocks += spoken(source, line.span, BlockRole.PARENTHETICAL, marks)
                last = BlockRole.PARENTHETICAL
            } else {
                said += line
            }
        }

        flush()
        return blocks
    }

    private companion object {
        /** The roles a blank-line-with-a-space may continue, and a parenthetical may follow. */
        val SPEECH = setOf(BlockRole.CHARACTER, BlockRole.DIALOGUE, BlockRole.PARENTHETICAL)

        const val DUAL_MARKER = "^"

        /** Where a scene number lives, for a renderer that puts it in the margin. */
        const val SCENE_NUMBER = "scene"

        /** 5.4's two-column layout, recorded where a layout decision belongs rather than as a role. */
        const val DUAL_CLASS = "dual"

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
