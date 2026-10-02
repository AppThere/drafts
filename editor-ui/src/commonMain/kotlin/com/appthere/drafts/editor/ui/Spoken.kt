package com.appthere.drafts.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.appthere.drafts.a11y.BlockName
import com.appthere.drafts.a11y.BlockNames
import com.appthere.drafts.a11y.spoken
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.announced
import com.appthere.drafts.i18n.resources.blocks_joined
import com.appthere.drafts.i18n.resources.said_after
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

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
@Composable
internal fun Modifier.spoken(spoken: Spoken): Modifier {
    // Resolved here and not inside `semantics`, which is not a composition: 11.1's words come from
    // resources, and a resource is read where a composition can see it. Only blocks that take a
    // prefix read anything -- which is every kind but a paragraph and a heading, and those are most
    // of a document.
    val described =
        spoken.prefix?.let { prefix ->
            stringResource(Res.string.said_after, prefix.spoken(), spoken.text)
        }

    return semantics {
        if (spoken.heading) heading()
        described?.let { contentDescription = it }
    }
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

    var change by remember { mutableStateOf<Change?>(null) }
    var last by remember { mutableStateOf<Pair<BlockId, Block>?>(null) }
    var lastCount by remember { mutableIntStateOf(count) }

    LaunchedEffect(focused, block, count) {
        val previous = last
        if (block != null && previous != null) {
            changeIn(
                previous,
                focused,
                block,
                merged =
                    count < lastCount && state.blocks.none { it.id == previous.first },
            )?.let { change = it }
        }
        last = focused?.let { id -> block?.let { id to it } }
        lastCount = count
    }

    LaunchedEffect(change) {
        if (change != null) {
            delay(CLEAR_AFTER_MILLIS)
            change = null
        }
    }

    // The words are read here rather than in the effect above, for the same reason the prefix is:
    // this is the composition, and 11.1's resources are read from one.
    val announcement =
        when (val said = change) {
            null -> ""
            is Change.Became -> stringResource(Res.string.announced, said.name.spoken())
            Change.Joined -> stringResource(Res.string.blocks_joined)
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
 * What happened to the edited block, before anyone has looked up the words for it.
 *
 * The same reason [Spoken] carries a [BlockName]: this is decided in an effect and said in the
 * composition, and only one of those two places can read a resource.
 */
@Immutable
private sealed interface Change {
    /** The block changed kind, and the new kind is what gets announced. */
    data class Became(
        val name: BlockName,
    ) : Change

    /** The block that had the caret is gone, and the caret is in the one it merged into. */
    data object Joined : Change
}

/**
 * What to say about the edited block now, given what it was: its new kind if the same block changed
 * kind, or that it joined the one above if the block that had the caret is gone and the caret is in
 * its neighbour.
 */
private fun changeIn(
    previous: Pair<BlockId, Block>,
    focused: BlockId?,
    block: Block,
    merged: Boolean,
): Change? =
    when {
        previous.first == focused -> BlockNames.changed(previous.second, block)?.let(Change::Became)
        merged -> Change.Joined
        else -> null
    }

/** Long enough to have been spoken; short enough that the next structural edit is heard. */
private const val CLEAR_AFTER_MILLIS = 3_000L
