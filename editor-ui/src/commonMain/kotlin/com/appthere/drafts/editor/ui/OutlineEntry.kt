package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Immutable
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.plainText
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.editor.engine.EditorBlock

/**
 * One line of `appthere-drafts.md` 10.1's outline: a heading, where it is, and how deep.
 *
 * [index] is the block's position in the document, which is what the list scrolls to; [id] is what
 * the caret is placed in once it gets there. Both, because they answer different questions and the
 * first stops being true as soon as a block is added above.
 */
@Immutable
data class OutlineEntry(
    val id: BlockId,
    val index: Int,
    val level: Int,
    val text: String,
)

/**
 * The document's heading hierarchy, in document order.
 *
 * 10.1: "An **outline view** exposing the heading/scene hierarchy as a navigable list is
 * disproportionately valuable for screen reader users, who cannot skim. Treat it as an
 * accessibility feature, not a convenience feature."
 *
 * Headings only, for now. Fountain's scene headings are the other half of "heading/scene" and
 * arrive with Fountain in Phase 7; they are a different block type and will be another branch here
 * rather than a different list.
 *
 * The words are the heading's own, with its inline markup resolved: a heading written
 * `## The *salt* road` reads "The salt road". A reader skimming the outline is looking for where
 * they are in the document, not for what the source looks like -- and someone hearing it read
 * aloud would otherwise hear the asterisks.
 *
 * Empty headings are kept. `##` with nothing after it is a real position in the document and a
 * common one while writing; dropping it would make the outline jump over the line being typed.
 */
fun outlineOf(blocks: List<EditorBlock>): List<OutlineEntry> =
    blocks.mapIndexedNotNull { index, editorBlock ->
        (editorBlock.block as? Heading)?.let { heading ->
            OutlineEntry(
                id = editorBlock.id,
                index = index,
                level = heading.level,
                text = heading.inlines.plainText().trim(),
            )
        }
    }
