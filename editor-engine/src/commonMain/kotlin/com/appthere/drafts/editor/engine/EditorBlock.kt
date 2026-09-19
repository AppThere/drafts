package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.Block
import kotlin.jvm.JvmInline

/**
 * A block's identity, stable across reparses.
 *
 * `engineering-conventions.md` 4.2 is blunt about why this exists: "`LazyColumn` without stable
 * keys. In the block editor this causes focus and caret loss on structural edits -- a correctness
 * bug, not a performance one. `key = { it.id }`, always."
 *
 * Identity has to survive a reparse that produced a *different* [Block] instance for the same
 * paragraph, or every keystroke would look like a delete and an insert to the list, and the field
 * the user is typing into would be destroyed and recreated underneath them.
 */
@JvmInline
value class BlockId(
    val value: Long,
) {
    override fun toString(): String = "block:$value"
}

/**
 * A block in an open document: the parsed [Block] plus the identity the editor tracks it by.
 *
 * Identity lives here rather than on [Block] deliberately. It is an *editing* concern -- an export
 * backend has no use for it, and `appthere-drafts.md` 3 keeps `:core-model` free of anything the
 * rest of the pipeline does not need. Putting an id on every IR node would also make two
 * structurally identical paragraphs unequal, which is a surprising thing for a document model to do.
 */
data class EditorBlock(
    val id: BlockId,
    val block: Block,
)

/** Hands out block ids. One per open document; ids are unique within a session, not globally. */
internal class BlockIds {
    private var next = 0L

    fun next(): BlockId = BlockId(next++)
}
