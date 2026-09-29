package com.appthere.drafts.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appthere.drafts.a11y.BlockNames
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.i18n.Strings
import kotlinx.coroutines.delay

/**
 * 10.1's semantics for a block shown in preview.
 *
 * A heading is a heading, which is what lets a screen reader move between them. Any other kind is
 * named before its words -- "Block quote, ..." -- and the words are part of the description, not
 * left beside it: a description *replaces* a node's text for a screen reader, so a bare prefix would
 * announce "Block quote" and nothing of the quote.
 *
 * Only the preview. The block being edited is a text field exposing its raw source, markup and all,
 * which already says what kind of block it is -- and 10.1 is emphatic that "the announced text and
 * the editable text" must never disagree.
 */
internal fun Modifier.spoken(spoken: Spoken): Modifier =
    semantics {
        if (spoken.heading) heading()
        spoken.prefix?.let { contentDescription = "$it, ${spoken.text}" }
    }

/**
 * 10.1's announcement of structural edits: "Structural edits (block promoted to heading, blocks
 * merged) get a `liveRegion` polite announcement: 'Heading level 2.'"
 *
 * Only structure, and only in the block being edited. Moving the caret into another block is not
 * announced -- reveal and preview are "visual affordances, not content changes" -- and neither is
 * typing that leaves a block the kind it was.
 *
 * An announcement is cleared a few seconds after it is made, so the same words said again -- a second
 * paragraph promoted to the same level -- are a change the live region reports rather than nothing.
 */
@Composable
internal fun StructureAnnouncer(
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val focused = state.caret?.block
    val block = focused?.let { id -> state.blocks.firstOrNull { it.id == id }?.block }
    val count = state.blocks.size

    var announcement by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<Pair<BlockId, Block>?>(null) }
    var lastCount by remember { mutableIntStateOf(count) }

    LaunchedEffect(focused, block, count) {
        val previous = last
        if (block != null && previous != null) {
            announcementOf(
                previous,
                focused,
                block,
                merged =
                    count < lastCount && state.blocks.none { it.id == previous.first },
            )?.let { announcement = it }
        }
        last = focused?.let { id -> block?.let { id to it } }
        lastCount = count
    }

    LaunchedEffect(announcement) {
        if (announcement.isNotEmpty()) {
            delay(CLEAR_AFTER_MILLIS)
            announcement = ""
        }
    }

    Box(
        modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (announcement.isNotEmpty()) contentDescription = announcement
            },
    )
}

/**
 * What to say about the edited block now, given what it was: its new kind if the same block changed
 * kind, or that it joined the one above if the block that had the caret is gone and the caret is in
 * its neighbour.
 */
private fun announcementOf(
    previous: Pair<BlockId, Block>,
    focused: BlockId?,
    block: Block,
    merged: Boolean,
): String? =
    when {
        previous.first == focused -> BlockNames.announcement(previous.second, block)
        merged -> Strings.BLOCKS_JOINED
        else -> null
    }

/** Long enough to have been spoken; short enough that the next structural edit is heard. */
private const val CLEAR_AFTER_MILLIS = 3_000L
