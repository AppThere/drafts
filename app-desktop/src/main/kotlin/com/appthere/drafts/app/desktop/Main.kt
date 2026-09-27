package com.appthere.drafts.app.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.appthere.drafts.app.DocumentOpening
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.app.rememberOpenDocument
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore

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

        Window(onCloseRequest = ::exitApplication, title = Strings.WINDOW_TITLE) {
            if (path == null) {
                DraftsApp(initialText = SampleDocument.TEXT)
            } else {
                FileDocument(path)
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
private fun FileDocument(path: String) {
    val store = remember { PathDocumentStore() }
    val ref = remember(path) { DocumentRef(path) }

    when (val opening = rememberOpenDocument(store, ref)) {
        is DocumentOpening.Opened -> DraftsApp(document = opening.document)
        DocumentOpening.Opening -> Notice(Strings.OPENING)
        is DocumentOpening.Failed -> Notice("${Strings.COULD_NOT_OPEN}\n\n$path\n\n${opening.detail}")
    }
}

/** A message on its own in the window, for when there is no document to show. */
@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text)
    }
}
