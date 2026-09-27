package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.ProseRole
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.engine.EditorBlock
import com.appthere.drafts.editor.engine.Selection
import com.appthere.drafts.editor.engine.UndoHistory
import com.appthere.drafts.editor.engine.caretAfter
import com.appthere.drafts.editor.engine.caretBefore
import com.appthere.drafts.editor.engine.changeBetween
import com.appthere.drafts.editor.engine.delete
import com.appthere.drafts.editor.engine.editRecording
import com.appthere.drafts.editor.engine.mergeWithPrevious
import com.appthere.drafts.editor.engine.redo
import com.appthere.drafts.editor.engine.selectAll
import com.appthere.drafts.editor.engine.sourceOf
import com.appthere.drafts.editor.engine.spanOf
import com.appthere.drafts.editor.engine.split
import com.appthere.drafts.editor.engine.textIn
import com.appthere.drafts.editor.engine.undo

/**
 * The editor's observable state: the block list, and where the caret is.
 *
 * A thin layer over [DocumentSession] and nothing more. The session is deliberately free of Compose
 * (`engineering-conventions.md` 4.2 warns it "will attract everything"), and the caret navigation
 * and structural edits are pure functions in `:editor-engine` that a test can call without a
 * composition. What this class adds is the one thing that has to live here: telling Compose when
 * those results have changed.
 *
 * Caret rather than focus. Tracking only *which* block has focus is enough to switch between
 * preview and reveal, and not enough for anything 4.3 asks for -- crossing a boundary, splitting,
 * merging and structural reparse all turn on the offset as well as the block.
 */
@Stable
class EditorState(
    private val session: DocumentSession,
    private val history: UndoHistory = UndoHistory(),
) {
    var blocks: List<EditorBlock> by mutableStateOf(session.blocks)
        private set

    /** Null when nothing has focus, which is when every block is in preview state. */
    var caret: Caret? by mutableStateOf(null)
        private set

    /**
     * What is selected, or null for nothing.
     *
     * Separate from [caret] because they answer different questions and are not always both live: a
     * selection spanning blocks has no single field to put a caret in, and 4.4 puts it in the engine
     * as offsets precisely so it does not need one.
     */
    var selection: Selection? by mutableStateOf(null)
        private set

    /**
     * Everything a row draws, built once and reused while the block's text is unchanged.
     *
     * An edit shifts the spans of every block below it, so on each keystroke every visible block
     * arrives as a new value and anything remembered against it is thrown away. Measured: typing
     * where fourteen blocks shift cost 8.3ms a keystroke against 5.0ms where none did.
     *
     * Keyed on the block's source text, which a shift does not change, so a shifted block gets back
     * the identical [RowContent] -- the same preview, because the preview map is block-relative, and
     * with it the same [androidx.compose.ui.text.AnnotatedString]. Nothing the row is given has
     * changed, so Compose skips it, and the text is not laid out again.
     */
    internal fun rowContentOf(
        editorBlock: EditorBlock,
        muted: Color,
    ): RowContent {
        val source = sourceOf(editorBlock.block)
        val cached = rows[editorBlock.id]

        if (cached != null && cached.source == source && cached.muted == muted) return cached.content

        // Bounded: only blocks that have been on screen are in here, but a long session scrolling a
        // long document would still accumulate. Dropping the lot is fine -- it rebuilds on demand.
        if (rows.size > ROW_CACHE_LIMIT) rows.clear()

        val content =
            RowContent(
                preview = previewOfBlock(editorBlock.block, muted),
                source = source,
                role = roleOf(editorBlock.block),
                softWrapped = editorBlock.block is Paragraph,
            )
        rows[editorBlock.id] = CachedRow(source, muted, content)
        return content
    }

    /**
     * The block with this id, or null if it has gone.
     *
     * Read where the span is needed rather than passed in with the rest of the row, because the span
     * is the one thing an edit above changes -- passing it would undo the whole point of
     * [previewOf], which is that a block that merely moved is handed back unchanged.
     */
    fun blockOf(id: BlockId): Block? = blocks.firstOrNull { it.id == id }?.block

    /** The raw source of a block, which is what its field shows in reveal state. */
    fun sourceOf(block: Block): String = session.sourceOf(block)

    /** The whole document, which the narrowing in [replace] reads the old block text out of. */
    private val text: String get() = session.text

    private val rows = mutableMapOf<BlockId, CachedRow>()

    fun place(caret: Caret) {
        this.caret = caret
        selection = null
    }

    /**
     * Starts a selection at [caret], dropping out of reveal state.
     *
     * Leaving the field is what makes the layer uniform -- every block in preview, one kind of text
     * layout to hit-test against -- and it costs nothing visually, because 4.2's reserved heights
     * mean a block that stops being focused does not change size. The mitigation for one problem
     * turns out to remove another.
     */
    fun beginSelection(at: Caret) {
        caret = null
        selection = Selection.at(at)
    }

    /** Drags the far end of the selection to [caret], leaving the anchor where it was. */
    fun extendSelection(to: Caret) {
        val current = selection ?: return
        selection = current.copy(focus = to)
    }

    /** Ends a drag: a selection that never moved is just a click, and places the caret. */
    fun endSelection() {
        val current = selection ?: return
        if (current.isCollapsed) {
            place(current.anchor)
        }
    }

    fun selectAll() {
        caret = null
        selection = session.selectAll()
    }

    /** The document span the selection covers, which each block clips against to draw itself. */
    fun selectedSpan(): SourceSpan? = selection?.let { session.spanOf(it) }

    /** The raw source the selection covers, which is what 4.4 says a copy yields. */
    fun selectedText(): String = selection?.let { session.textIn(it) }.orEmpty()

    /** Deletes the selection, against the source rather than against any field. */
    fun deleteSelection(): Boolean {
        val left = selection?.takeIf { !it.isCollapsed }?.let { session.delete(it, history) } ?: return false
        blocks = session.blocks
        selection = null
        caret = left
        return true
    }

    /**
     * Replaces the focused block's source with what the field now holds.
     *
     * The field reports its whole contents, but what is *recorded* is narrowed to the run that
     * actually changed. Recording the whole block would make every keystroke a replacement of
     * everything, which no amount of care in [UndoHistory] can coalesce -- undo would step back one
     * character at a time. See [changeBetween].
     */
    fun replace(
        span: SourceSpan,
        replacement: String,
        offset: Int,
    ) {
        val block = caret?.block ?: return
        val existing = text.substring(span.start.value, span.endExclusive.value)
        val change = changeBetween(existing, replacement) ?: return

        session.editRecording(
            history,
            SourceSpan.of(span.start.value + change.start, span.start.value + change.endExclusive),
            change.replacement,
        )
        blocks = session.blocks
        caret = Caret(block, offset)
    }

    /** Enter. The caret follows to the head of the new block. */
    fun split() {
        val from = caret ?: return
        val moved = session.split(from, history) ?: return
        blocks = session.blocks
        caret = moved
    }

    /** Backspace at offset 0. Null from the engine means there is nothing above to merge into. */
    fun mergeWithPrevious(): Boolean {
        val moved = caret?.let { session.mergeWithPrevious(it, history) } ?: return false
        blocks = session.blocks
        caret = moved
        return true
    }

    /**
     * Undoes the last edit, putting the caret back where the edit started.
     *
     * The selection is cleared rather than restored. Restoring it would mean recording it alongside
     * every revision, and a selection that reappears around text the user has just watched change
     * is more startling than none at all.
     */
    fun undo(): Boolean {
        val moved = session.undo(history) ?: return false
        blocks = session.blocks
        selection = null
        caret = moved
        return true
    }

    /** Redoes the last undone edit. */
    fun redo(): Boolean {
        val moved = session.redo(history) ?: return false
        blocks = session.blocks
        selection = null
        caret = moved
        return true
    }

    /** Leaves the block upwards, arriving at the end of the one above. False at the top. */
    fun moveToPrevious(): Boolean {
        val moved = caret?.let { session.caretBefore(it.block) } ?: return false
        caret = moved
        return true
    }

    /** Leaves the block downwards, arriving at the start of the one below. False at the bottom. */
    fun moveToNext(): Boolean {
        val moved = caret?.let { session.caretAfter(it.block) } ?: return false
        caret = moved
        return true
    }
}

/**
 * What a row draws, and nothing about where its block sits in the file.
 *
 * The distinction is the whole point. An edit shifts the span of every block below it, so a row
 * handed its `Block` is handed something new on every keystroke and has to be composed again -- for
 * a change to a number it never draws. Everything here is equal across a shift. The span is
 * fetched from the state by the two places that genuinely need it, and one of those reads it at
 * draw time so that it invalidates drawing rather than composition.
 */
@Immutable
internal data class RowContent(
    val preview: BlockPreview,
    val source: String,
    val role: ProseRole,
    /**
     * Whether this block's newlines are the author's wrapping rather than its structure.
     *
     * True for paragraphs and nothing else, because a paragraph is the only block CommonMark reads
     * a bare newline in as a *soft* break -- something the parser turns into a space. In a fenced
     * code block the newlines are the code's own lines; between list items they separate the items;
     * in a table they separate the rows. Reflowing any of those would put the whole block on one
     * line, which is how the first version of this got it wrong: a four-line code block drew as one
     * line and reserved the other three.
     */
    val softWrapped: Boolean,
)

/**
 * Which role in the prose scale a block is set in.
 *
 * The role, not the resolved style: the style depends on the reader's settings and the palette,
 * which are read from the composition, and this is built outside it. The role is a property of the
 * block alone, so it belongs with the rest of the row's shift-independent content.
 */
internal fun roleOf(block: Block): ProseRole =
    when (block) {
        is Heading -> Prose.heading(block.level)
        is CodeBlock -> Prose.Code
        is BlockQuote -> Prose.Quote
        else -> Prose.Body
    }

/** A row's content and the things it was built from, so a change in any of them rebuilds it. */
private class CachedRow(
    val source: String,
    val muted: Color,
    val content: RowContent,
)

/** Enough for several screens of blocks; past that the cache is dropped rather than pruned. */
private const val ROW_CACHE_LIMIT = 200
