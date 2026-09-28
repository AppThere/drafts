package com.appthere.drafts.app.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.appthere.drafts.app.DocumentOpening
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.rememberOpenDocument
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.Recovery
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.SnapshotTrigger
import com.appthere.drafts.platform.files.WindowRecord
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.files.desktopSessionRoot
import com.appthere.drafts.platform.files.epochMillis
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.intents.SingleInstance
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.awt.Desktop

/**
 * The desktop entry point.
 *
 * `appthere-drafts.md` 7.2: "Compose Desktop's `application { }` scope hosts multiple `Window`
 * composables. Maintain a `List<DocumentSession>` in the application state and emit one `Window`
 * per entry, each with its own `WindowState` (position, size, placement) persisted."
 *
 * The list is [SessionList]'s, so it survives the process. A path on the command line joins it; the
 * sessions from last time rejoin it on launch. With neither, the window comes up on the sample
 * document, which is the Phase 2 behaviour the gate criteria still depend on.
 *
 * File > Open is still absent. It needs a platform file dialog, which is `:platform-intents`.
 */
fun main(args: Array<String>) {
    val instance = SingleInstance()
    val opened = Channel<String>(Channel.UNLIMITED)

    if (!becameTheRunningInstance(instance, args, opened)) return

    application { DraftsApplication(args, opened) }
    instance.release()
}

/**
 * Decides whether this launch is the application or a message to it.
 *
 * 9.4: "Route to an existing instance ... rather than launching a second process", and "an
 * already-running instance should open the document in a **new window**, not replace the current
 * one."
 *
 * Claims first and hands off only if the claim fails, so the decision is the one the operating
 * system arbitrated rather than a guess about who started when. A launch that cannot claim and has
 * nothing to hand over has nothing to do: the running instance is already showing everything.
 */
private fun becameTheRunningInstance(
    instance: SingleInstance,
    args: Array<String>,
    opened: Channel<String>,
): Boolean {
    if (!instance.claim { path -> opened.trySend(path) }) {
        args.firstOrNull()?.let { instance.handOff(it) }
        return false
    }
    installOpenFileHandler(opened)
    return true
}

/**
 * 9.4 on macOS: "handle the open-document Apple Event via `java.awt.Desktop.setOpenFileHandler`".
 *
 * That is how a double-click reaches a running instance there; the path does not arrive as an
 * argument the way it does on Windows and Linux. Unsupported everywhere else, which is not an
 * error -- and `Desktop` throws rather than reporting on some headless setups, so the whole thing
 * is guarded.
 */
private fun installOpenFileHandler(opened: Channel<String>) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
            Desktop.getDesktop().setOpenFileHandler { event -> event.files.forEach { opened.trySend(it.path) } }
        }
    }
}

/**
 * The application: 7.2's session list, one `Window` per entry.
 *
 * "Compose Desktop's `application { }` scope hosts multiple `Window` composables. Maintain a
 * `List<DocumentSession>` in the application state and emit one `Window` per entry, each with its
 * own `WindowState` (position, size, placement) persisted."
 */
@Composable
private fun ApplicationScope.DraftsApplication(
    args: Array<String>,
    opened: Channel<String>,
) {
    val store = remember { PathDocumentStore() }
    val snapshots = remember { SnapshotStore(store, desktopSessionRoot()) }
    val sessions = remember { SessionList(snapshots) }

    val open = remember { mutableStateListOf<SessionRecord>() }
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 8.3's pruning, before anything is restored: a session whose work reached a file more
        // than thirty days ago has no snapshot worth reopening.
        snapshots.prune(epochMillis())

        open += sessions.restorable()
        args.firstOrNull()?.let { open.show(it, sessions) }
        restored = true

        // Documents handed over by later launches, and by macOS. A new window each, per 9.4.
        for (path in opened) open.show(path, sessions)
    }

    open.forEach { record ->
        // Keyed on the document, so dragging one window does not recompose another's.
        key(record.documentId) {
            DocumentWindow(
                record = record,
                sessions = sessions,
                snapshots = snapshots,
                store = store,
                onClose = { open.removeAll { it.documentId == record.documentId } },
            )
        }
    }

    // An invisible window while the session list is being read.
    //
    // `application { }` exits the moment its composition holds no windows, and reading the
    // sessions is I/O that finishes a frame or two later -- so without something here the
    // application starts, finds nothing to show, and quits before the restore arrives. Found by
    // running it: the process exited in under a second with the session file already written.
    // Invisible rather than a "loading" window, because a real one would flash up and be replaced
    // by windows in different places.
    //
    // It also keeps a first instance launched with no document alive, so it is there to receive
    // one.
    if (!restored) {
        Window(onCloseRequest = ::exitApplication, visible = false, title = Strings.WINDOW_TITLE) {}
    }

    // Nothing to restore and nothing asked for: the sample, in a window of its own that no session
    // knows about. There is no document to record, because there is no file.
    if (restored && open.isEmpty()) {
        Window(onCloseRequest = ::exitApplication, title = Strings.WINDOW_TITLE) {
            DraftsApp(initialText = SampleDocument.TEXT)
        }
    }
}

/**
 * Adds a document to the session list and to the windows on screen, unless it is already there.
 *
 * Already-open is the ordinary case when a reader double-clicks a file they have open: 9.4 asks
 * for a new window per document, not per double-click.
 */
private suspend fun MutableList<SessionRecord>.show(
    path: String,
    sessions: SessionList,
) {
    val kind = DocumentKind.of(path) ?: DocumentKind.Markdown
    val record = sessions.opened(desktopIdentity(path, kind.id))

    if (none { it.documentId == record.documentId }) this += record
}

/**
 * One document, in one window, remembering where it was put.
 *
 * The geometry is written back through [SessionList] rather than held in memory, because 7.3 asks
 * for it to survive the process. It is debounced: dragging a window emits a position per frame, and
 * a write per frame would put the reader's own disk under the thing they are dragging.
 */
@Composable
private fun ApplicationScope.DocumentWindow(
    record: SessionRecord,
    sessions: SessionList,
    snapshots: SnapshotStore,
    store: PathDocumentStore,
    onClose: () -> Unit,
) {
    val state =
        rememberWindowState(
            position = record.window?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            size = record.window?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(defaultWidth, defaultHeight),
            placement = placementOf(record.window?.placement),
        )
    var closing by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var closed by remember { mutableStateOf(false) }

    // Read through `rememberUpdatedState` because the effects below outlive the recomposition that
    // starts them, and a closing window must not call back into a frame that has already gone.
    val forget by rememberUpdatedState(onClose)

    Window(
        onCloseRequest = { closed = true },
        state = state,
        title = record.displayName,
        // 12: "**Full-screen** is a first-class mode on every platform that has one."
        //
        // Handled at the window rather than inside the document, because the placement belongs to
        // the window and nothing below it can reach one. F11 is the convention on Linux and
        // Windows; macOS uses Ctrl+Cmd+F, which arrives here as the same event with meta held.
        onPreviewKeyEvent = { event ->
            if (togglesFullScreen(event)) {
                state.placement =
                    if (state.placement == WindowPlacement.Fullscreen) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Fullscreen
                    }
                true
            } else {
                false
            }
        },
    ) {
        FileDocument(
            record = record,
            snapshots = snapshots,
            store = store,
            onReadyToClose = { closing = it },
        )
    }

    LaunchedEffect(state) {
        // `collectLatest` plus a delay rather than `debounce`, which is still a preview API: a new
        // geometry cancels the pending write, so dragging a window is one write when it stops
        // rather than one per frame while it moves.
        snapshotFlow { state.geometry() }
            .distinctUntilChanged()
            .collectLatest { geometry ->
                delay(SETTLE_MILLIS)
                sessions.remember(record.documentId, geometry)
            }
    }

    // Closing runs to completion before the window goes: 8.1 lists window close among its triggers
    // and it is the one that gets no second chance. The composition is still alive here, which it
    // would not be if this ran after the window had gone.
    if (closed) {
        LaunchedEffect(Unit) {
            closing?.invoke()
            sessions.closed(record.documentId)
            forget()
        }
    }
}

/**
 * A document read from disk, and the two states before it is one.
 *
 * `Opening` is usually a single frame for a local file and is not always: the path may be on a
 * network share or a sync provider that has to fetch the contents first. `Failed` is ordinary too
 * -- 7.3 warns that a restored session's file may have vanished -- and a reader who was handed a
 * document that is not there should be told so rather than shown an empty one.
 */
@Composable
private fun FileDocument(
    record: SessionRecord,
    snapshots: SnapshotStore,
    store: PathDocumentStore,
    onReadyToClose: (suspend () -> Unit) -> Unit,
) {
    val ref = remember(record.documentId) { DocumentRef(record.accessToken ?: record.uri) }
    val identity = remember(record.documentId) { record.identity() }
    val recover: suspend (Digest) -> Recovery =
        remember(identity) { { digest -> snapshots.examine(identity.documentId, digest) } }

    when (val opening = rememberOpenDocument(store, ref, recover)) {
        is DocumentOpening.Opened -> {
            val keeper = remember(opening.document) { SnapshotKeeper(opening.document, snapshots, identity) }

            onReadyToClose { keeper.snapshotOn(SnapshotTrigger.Closing, scrollOffset = 0) }

            DraftsApp(document = opening.document, keeper = keeper)
        }

        DocumentOpening.Opening -> {
            Notice(Strings.OPENING)
        }

        is DocumentOpening.Failed -> {
            Notice("${Strings.COULD_NOT_OPEN}\n\n${record.displayName}\n\n${opening.detail}")
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

private fun SessionRecord.identity() =
    SessionIdentity(
        documentId = documentId,
        uri = uri,
        displayName = displayName,
        kind = kind,
        accessToken = accessToken,
    )

/**
 * F11, or Ctrl+Cmd+F where that is the convention.
 *
 * Checked before the document sees it: full screen is a window operation, and a key the editor
 * might otherwise take is one the reader could not use to leave full screen again.
 */
private fun togglesFullScreen(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.key == Key.F11 || (event.isCtrlPressed && event.isMetaPressed && event.key == Key.F))

/** 7.3's `window` record: "x, y, width, height, placement". */
private fun WindowState.geometry() =
    WindowRecord(
        x = position.x.value.toInt(),
        y = position.y.value.toInt(),
        width = size.width.value.toInt(),
        height = size.height.value.toInt(),
        placement = placement.name,
    )

/** Unknown or unrecognised placements come back floating, which is what a new window would be. */
private fun placementOf(name: String?): WindowPlacement =
    WindowPlacement.entries.firstOrNull { it.name == name } ?: WindowPlacement.Floating

private val defaultWidth = 900.dp
private val defaultHeight = 1100.dp

/** Long enough that dragging a window is one write rather than one per frame. */
private const val SETTLE_MILLIS = 400L
