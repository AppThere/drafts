package com.appthere.drafts.editor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.FootnoteRef
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.LineBreak
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Strikethrough
import com.appthere.drafts.core.model.Table
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.ThematicBreak
import com.appthere.drafts.core.model.Underline

/**
 * Builds the **preview** rendering of a block: formatted, with markup characters hidden.
 *
 * `appthere-drafts.md` 4.1 draws the line precisely. Preview hides markup and applies inline
 * decoration; reveal shows the raw source with markup visible and *not* formatted. What never
 * changes between the two is the block's own metrics -- heading size, typeface, indentation --
 * which is what keeps the line from reflowing when focus enters it.
 *
 * Containers are flattened into one styled string rather than nested composables, because the
 * engine treats a list or a quote as a single block: one unit of focus, one entry in the
 * `LazyColumn`, one thing that reveals. Rendering it as several rows would put the two models out
 * of step and break the key that keeps focus alive.
 *
 * Every append carries the source span it came from, so the result knows its own provenance -- see
 * [BlockPreview]. That is what lets a click land the caret where it was aimed and a selection drawn
 * over preview glyphs turn back into source to copy.
 */
internal fun previewOfBlock(
    block: Block,
    muted: Color,
    collapseNotes: Boolean = false,
): BlockPreview =
    PreviewBuilder(blockStart = block.source?.start?.value ?: 0)
        .apply {
            when {
                // 4.5: a whole boneyard, collapsed, is its own delimiters around nothing.
                collapseNotes && block.role == BlockRole.NOTE -> styled(dim(muted)) { decoration(BONEYARD_FOLDED) }

                block.role in asides -> styled(dim(muted)) { appendBlock(block, muted, collapseNotes) }

                else -> appendBlock(block, muted, collapseNotes)
            }
        }.build()

/**
 * Whether [block] holds a note or a boneyard, which [previewOfBlock] folds away when collapsing.
 *
 * A row that has folded something away reserves only its preview's height: 4.2's reservation of the
 * taller state would keep the room the note took and show nothing in it.
 */
internal fun holdsNotes(block: Block): Boolean =
    block.role == BlockRole.NOTE || (block as? Paragraph)?.inlines?.any(::isNote) == true

private fun isNote(inline: Inline): Boolean =
    when (inline) {
        is RawInline -> inline.origin == Origin.FOUNTAIN_NOTE || inline.origin == Origin.FOUNTAIN_BONEYARD
        is Emphasis -> inline.children.any(::isNote)
        is Underline -> inline.children.any(::isNote)
        is Strikethrough -> inline.children.any(::isNote)
        else -> false
    }

private fun PreviewBuilder.appendBlock(
    block: Block,
    muted: Color,
    collapseNotes: Boolean = false,
) {
    when (block) {
        is Paragraph -> appendInlines(block.inlines, NoteStyle(muted, collapseNotes))
        is Heading -> appendInlines(block.inlines)
        is CodeBlock -> appendCode(block, muted)
        is ListBlock -> appendList(block, muted)
        is BlockQuote -> appendQuote(block, muted)
        is ThematicBreak -> styled(dim(muted)) { append(RULE, block.source) }
        is Table -> appendTable(block, muted)
        else -> Unit
    }
}

/**
 * Code, with its fences shown but dimmed.
 *
 * The exception to 4.1's "markup characters hidden", and a deliberate one. Hiding the fences costs
 * two lines, and 4.2 requires a block to reserve the taller of its two states -- so every fenced
 * block in the document carried two blank lines of slack under it, in every state, for the sake of
 * a transition. Dimming instead of hiding makes the two states the same height, and the slack
 * disappears because there is nothing left to reserve.
 *
 * The fence is rebuilt from the IR exactly as `BlockWriter.code` writes it -- same character, same
 * length, same info string -- so what the preview shows is what the file says. Because the two
 * states then have the same shape, the whole block maps to its own source one for one.
 */
private fun PreviewBuilder.appendCode(
    block: CodeBlock,
    muted: Color,
) {
    val fence = block.fence
    if (fence == null) {
        // An indented block has no fence. Its markup *is* the four-space indent, and preview keeps
        // it: 4.1 lists "block-level indentation and alignment" among the things that never toggle,
        // and dropping it made the code sit left of where the caret would put it. Empty lines stay
        // empty, which is the rule `BlockWriter.code` follows when writing one back.
        val indented =
            block.text
                .trimEnd('\n')
                .lines()
                .joinToString("\n") { if (it.isEmpty()) it else INDENT + it }
        styled(monospace) { append(indented, block.source) }
        return
    }

    // In pieces rather than as one run, so the fences can be dimmed and each piece still maps to
    // its own stretch of source. The offsets assume the canonical spelling `BlockWriter` writes; a
    // block spelled unusually in the file (an indented fence, padding after the info string) drifts
    // within itself, but stays inside the block and stays monotonic.
    val rule = fence.char.toString().repeat(fence.length)
    val opening = rule + block.language.orEmpty()
    val body = block.text.trimEnd('\n')
    var at = 0

    styled(monospace.merge(dim(muted))) { append(opening, block.subSpan(at, opening.length)) }
    at += opening.length
    append("\n", block.subSpan(at, 1))
    at += 1
    styled(monospace) { append(body, block.subSpan(at, body.length)) }
    at += body.length
    append("\n", block.subSpan(at, 1))
    at += 1
    styled(monospace.merge(dim(muted))) { append(rule, block.subSpan(at, rule.length)) }
}

/** A stretch of a block's own source, [length] characters from [offset] into it. */
private fun Block.subSpan(
    offset: Int,
    length: Int,
): SourceSpan? {
    val span = source ?: return null
    val start = (span.start.value + offset).coerceIn(span.start.value, span.endExclusive.value)
    return SourceSpan.of(start, (start + length).coerceAtMost(span.endExclusive.value))
}

/**
 * A list, one line per item, with a real bullet rather than the marker the author typed.
 *
 * Hiding the `-` is the point: 4.1 puts markup characters in reveal state only. The bullet is
 * decoration the preview supplies, not text from the document, and so has no source to map to.
 */
private fun PreviewBuilder.appendList(
    block: ListBlock,
    muted: Color,
) {
    block.items.forEachIndexed { index, item ->
        if (index > 0) decoration("\n")
        styled(dim(muted)) {
            decoration(if (block.ordered) "${block.start + index}." + NBSP else BULLET + NBSP)
        }
        item.forEachIndexed { childIndex, child ->
            if (childIndex > 0) decoration("\n")
            appendBlock(child, muted)
        }
    }
}

/** A quote, with its `>` markers replaced by a rule and an indent. */
private fun PreviewBuilder.appendQuote(
    block: BlockQuote,
    muted: Color,
) {
    block.children.forEachIndexed { index, child ->
        if (index > 0) decoration("\n")
        styled(dim(muted)) { decoration(QUOTE_INDENT) }
        styled(quoted) { appendBlock(child, muted) }
    }
}

/** A table as spaced rows. Provisional: real table layout is Phase 3 work. */
private fun PreviewBuilder.appendTable(
    block: Table,
    muted: Color,
) {
    (listOf(block.header) + block.rows).forEachIndexed { index, row ->
        if (index > 0) decoration("\n")
        row.forEachIndexed { cellIndex, cell ->
            if (cellIndex > 0) styled(dim(muted)) { decoration(EM_SPACE) }
            appendInlines(cell)
        }
    }
}

/**
 * How Fountain's notes are drawn in a paragraph: dimmed, and folded to their delimiters when the
 * reader has collapsed them (4.5). Null where there are none to draw -- anything that is not a
 * Fountain paragraph.
 */
private class NoteStyle(
    val muted: Color,
    val collapsed: Boolean,
)

private fun PreviewBuilder.appendInlines(
    inlines: List<Inline>,
    notes: NoteStyle? = null,
) {
    inlines.forEach { appendInline(it, notes) }
}

private fun PreviewBuilder.appendInline(
    inline: Inline,
    notes: NoteStyle? = null,
) {
    when (inline) {
        is Text -> {
            append(inline.value, inline.source)
        }

        is Emphasis -> {
            styled(inline.style()) { appendInlines(inline.children, notes) }
        }

        is Strikethrough -> {
            styled(struck) { appendInlines(inline.children, notes) }
        }

        // Fountain's `_x_`, which underlines rather than italicises. The same decoration a link
        // gets, because that is what underline is -- the two are told apart by colour elsewhere.
        is Underline -> {
            styled(underlined) { appendInlines(inline.children, notes) }
        }

        is RawInline if notes != null && isNote(inline) -> {
            appendNote(inline, notes)
        }

        is CodeSpan -> {
            styled(monospace) { append(inline.text, inline.source) }
        }

        is Link -> {
            styled(linked) { appendInlines(inline.children) }
        }

        is Image -> {
            styled(linked) { append(inline.alt, inline.source) }
        }

        // 4.1 lists footnote refs as superscripted. Superscript changes line metrics, so the
        // marker is styled rather than raised until the dual measurement in 4.2 lands.
        is FootnoteRef -> {
            styled(linked) { append(inline.label, inline.source) }
        }

        // Shortcodes are opaque. Showing the source is the honest rendering: the editor has no idea
        // what Hugo will turn it into, and pretending otherwise would be a lie in the preview.
        is RawInline -> {
            styled(monospace) { append(inline.text, inline.source) }
        }

        is LineBreak -> {
            append(if (inline.hard) "\n" else " ", inline.source)
        }
    }
}

/**
 * 4.5: "Notes `[[ ]]`, Boneyard `/* */` -- Dimmed, collapsible". Collapsed, a note is its own
 * delimiters around an ellipsis: decoration, so a click on it lands beside it and a screen reader
 * does not read the brackets out.
 */
private fun PreviewBuilder.appendNote(
    inline: RawInline,
    notes: NoteStyle,
) {
    styled(dim(notes.muted)) {
        when {
            !notes.collapsed -> append(inline.text, inline.source)
            inline.origin == Origin.FOUNTAIN_NOTE -> decoration(NOTE_FOLDED)
            else -> decoration(BONEYARD_FOLDED)
        }
    }
}

private fun Emphasis.style(): SpanStyle =
    if (strong) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle(fontStyle = FontStyle.Italic)

private val struck = SpanStyle(textDecoration = TextDecoration.LineThrough)
private val monospace = SpanStyle(fontFamily = FontFamily.Monospace)
private val linked = SpanStyle(textDecoration = TextDecoration.Underline)
private val underlined = SpanStyle(textDecoration = TextDecoration.Underline)
private val quoted = SpanStyle(fontStyle = FontStyle.Italic)

/** Decoration the preview supplies, and markup it shows without giving it the weight of text. */
private fun dim(muted: Color) = SpanStyle(color = muted)

/** U+00A0, so the marker never wraps away from the text it belongs to. */
private const val NBSP = "\u00A0"
private const val BULLET = "•"

/** U+2003, wide enough to read as a column gap. */
private const val EM_SPACE = "\u2003"
private const val RULE = "────────"

/** A collapsed note and a collapsed boneyard: Fountain's own delimiters, so a writer knows each. */
private const val NOTE_FOLDED = "[[…]]"
private const val BONEYARD_FOLDED = "/* … */"

/**
 * The Fountain roles that are about the script rather than in it, which 4.5 dims: a whole-block
 * boneyard, a section and a synopsis.
 */
private val asides = setOf(BlockRole.NOTE, BlockRole.SECTION, BlockRole.SYNOPSIS)
private const val QUOTE_INDENT = "│  "

/** What an indented code block is indented by, per CommonMark and `BlockWriter`. */
private const val INDENT = "    "
