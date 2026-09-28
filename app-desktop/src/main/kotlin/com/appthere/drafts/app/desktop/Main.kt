package com.appthere.drafts.app.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.appthere.drafts.app.DocumentOpening
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.rememberOpenDocument
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.SnapshotTrigger
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.files.desktopSessionRoot

/**
 * The desktop entry point.
 *
 * A path on the command line opens that file through `:platform-files`, which is what makes the
 * document lifecycle of section 8 observable: the digest is recorded at open, the badge shows the
 * 8.4 state, and Ctrl+S goes through the digest check rather than straight to the disk.
 *
 * With no argument the window comes up on the sample document, in memory and belonging to no file.
 * That is the Phase 2 behaviour and the gate criteria still depend on it -- they are about what
 * happens when someone types into a long document, not about how it got there.
 *
 * File > Open is not here yet. It needs a platform file dialog, which is `:platform-intents` in
 * Phase 5; until then the command line is the honest way in rather than a menu item that cannot
 * work.
 */
fun main(args: Array<String>) =
    application {
        val path = args.firstOrNull()

        // Whatever the open document wants done before the window goes away. 8.1 lists window close
        // among its triggers, and it is the one trigger that has no later chance to run.
        var beforeClose: (suspend () -> Unit)? = null
        var closing by remember { mutableStateOf(false) }

        Window(onCloseRequest = { closing = true }, title = Strings.WINDOW_TITLE) {
            if (path == null) {
                DraftsApp(initialText = SampleDocument.TEXT)
            } else {
                FileDocument(path) { beforeClose = it }
            }
        }

        // The window stays up until the snapshot is down, rather than the main thread being blocked
        // until it is. On a local file that is a frame nobody sees; on a network share it is the
        // difference between a window that lingers for a moment and an application that has hung.
        if (closing) {
            LaunchedEffect(Unit) {
                beforeClose?.invoke()
                exitApplication()
            }
        }
    }

/**
 * A document read from disk, and the two states before it is one.
 *
 * `Opening` is usually a single frame for a local file and is not always: the path may be on a
 * network share or a sync provider that has to fetch the contents first. `Failed` is ordinary too --
 * a path that was deleted between being typed and being opened -- and a reader who was given a file
 * that is not there should be told so rather than shown an empty document.
 */
@Composable
private fun FileDocument(
    path: String,
    onReadyToClose: (suspend () -> Unit) -> Unit,
) {
    val store = remember { PathDocumentStore() }
    val ref = remember(path) { DocumentRef(path) }
    val snapshots = remember { SnapshotStore(store, desktopSessionRoot()) }

    when (val opening = rememberOpenDocument(store, ref)) {
        is DocumentOpening.Opened -> {
            val keeper =
                remember(opening.document) {
                    SnapshotKeeper(opening.document, snapshots, desktopIdentity(path))
                }

            // 8.1's "Window close, before teardown". Registered upwards rather than handled here,
            // because by the time the window is closing this composition is on its way out.
            onReadyToClose { keeper.snapshotOn(SnapshotTrigger.Closing, scrollOffset = 0) }

            DraftsApp(document = opening.document, keeper = keeper)
        }

        DocumentOpening.Opening -> {
            Notice(Strings.OPENING)
        }

        is DocumentOpening.Failed -> {
            Notice("${Strings.COULD_NOT_OPEN}\n\n$path\n\n${opening.detail}")
        }
    }
}

/** A message on its own in the window, for when there is no document to show. */
@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text)
    }
}
