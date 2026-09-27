package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.editor.engine.Caret
import kotlin.math.abs

/**
 * The custom selection layer `appthere-drafts.md` 4.4 asks for.
 *
 * The problem it exists to solve, in the spec's words: "Compose's `SelectionContainer` does not
 * compose with editable fields, and per-block fields mean no single text layout spans the
 * selection. This is the single largest unknown in the project."
 *
 * So the layer keeps, for each block on screen, the three things needed to turn a point into a
 * position in the document: where the block is, how its text was laid out, and where that text came
 * from in the file. A point becomes a block, then a preview offset, then a source offset -- and a
 * source offset is a thing the engine understands, which is what makes a selection that spans
 * blocks expressible at all.
 *
 * Only blocks that are composed are registered, so this is O(viewport) like everything else here. A
 * block scrolled out of view drops out; a selection that covers it is unaffected, because the
 * selection is held in the engine as offsets and does not depend on anything being on screen.
 */
@Stable
internal class SelectionLayer {
    private val blocks = mutableMapOf<BlockId, BlockGeometry>()
    private var container: LayoutCoordinates? = null

    fun onContainerPositioned(coordinates: LayoutCoordinates) {
        container = coordinates
    }

    fun onBlockPositioned(
        id: BlockId,
        coordinates: LayoutCoordinates,
        layout: TextLayoutResult,
        preview: BlockPreview,
    ) {
        blocks[id] = BlockGeometry(coordinates, layout, preview)
    }

    fun onBlockRemoved(id: BlockId) {
        blocks.remove(id)
    }

    /**
     * The position in the document under a point in the editor.
     *
     * A point below every block belongs to the last one and a point above every block to the first,
     * so dragging past the end of the document keeps extending the selection rather than stopping
     * dead at whatever block happened to be under the pointer last.
     */
    fun caretAt(point: Offset): Caret? {
        val container = container ?: return null

        val boxes =
            blocks.entries
                .filter { it.value.coordinates.isAttached }
                .map { it to container.localBoundingBoxOf(it.value.coordinates) }

        val hit =
            boxes.firstOrNull { (_, box) -> point.y >= box.top && point.y <= box.bottom }
                ?: boxes.minByOrNull { (_, box) ->
                    minOf(abs(point.y - box.top), abs(point.y - box.bottom))
                }

        // The map answers in block-relative offsets, which is what a `Caret` carries -- so the
        // answer needs no adjustment, and cannot be wrong about which block it is relative to.
        return hit?.let { (entry, _) ->
            val geometry = entry.value
            val local = geometry.coordinates.windowToLocal(container.localToWindow(point))
            geometry.preview
                .sourceOffsetAt(geometry.layout.getOffsetForPosition(local))
                ?.let { Caret(entry.key, it) }
        }
    }
}

private class BlockGeometry(
    val coordinates: LayoutCoordinates,
    val layout: TextLayoutResult,
    val preview: BlockPreview,
)
