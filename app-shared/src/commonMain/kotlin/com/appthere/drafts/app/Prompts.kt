package com.appthere.drafts.app

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appthere.drafts.platform.files.WriteOutcome
import kotlinx.coroutines.launch

/**
 * What the window asks or tells the reader about their document, beyond the status in its corner.
 *
 * 8.2's refusal, the one dialog 8.4 allows ("Dialogs only on attempted write"). 8.3's restored
 * work. A save that failed. And 7.3's vanished file, whose words are back but have no file. Each
 * is summoned by something that happened and stays until the reader answers it: fading something
 * the reader has yet to read would be the interface taking it away from them.
 */
@Composable
internal fun BoxScope.DocumentPrompts(
    document: OpenDocument,
    keeper: SnapshotKeeper?,
    saving: Saving,
    saveAs: (suspend () -> WriteOutcome?)?,
) {
    val scope = rememberCoroutineScope()

    // 8.3's banner. Separate state from `restoredFromSnapshot`, which is a fact about how the
    // document opened and does not stop being true once the reader has answered.
    var announceRestored by remember(document) { mutableStateOf(document.restoredFromSnapshot) }

    if (announceRestored) {
        RestoredBanner(
            onKeep = { announceRestored = false },
            onDiscard = {
                // Back to the file, and the snapshot goes with it. Reloading without discarding
                // would leave the snapshot to be restored again on the next launch, which is the
                // reader being asked the same question until they answer it differently.
                scope.launch {
                    document.reload()
                    keeper?.discard()
                    announceRestored = false
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(inset),
        )
    }

    // 7.3: "A document whose file has vanished opens ... from its snapshot with a clear banner
    // offering *Save As*." Gone once Save As has given it a file again.
    if (document.needsSaveAs && !document.isUntitled && saveAs != null) {
        FileGoneBanner(
            onSaveAs = { scope.launch { saving.saveAs(saveAs) } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(inset),
        )
    }

    if (saving.refusal != null) {
        ConflictDialog(
            onReload = {
                scope.launch {
                    document.reload()
                    saving.answered()
                }
            },
            onCancel = saving::answered,
            modifier = Modifier.align(Alignment.Center).padding(inset),
        )
    }

    // At the top, clear of the banners at the foot: they can be showing at once.
    if (saving.failed) {
        SaveFailedBanner(
            onDismiss = saving::acknowledged,
            modifier = Modifier.align(Alignment.TopCenter).padding(inset),
        )
    }
}

/** The same distance from the window's edge as the rest of the chrome. */
private val inset = 16.dp
