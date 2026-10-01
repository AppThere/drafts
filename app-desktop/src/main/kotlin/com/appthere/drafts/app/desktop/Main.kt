package com.appthere.drafts.app.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.appthere.drafts.app.KindChange
import com.appthere.drafts.app.Notice
import com.appthere.drafts.app.SampleDocument
import com.appthere.drafts.app.SaveAs
import com.appthere.drafts.app.SettingsStore
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.copyName
import com.appthere.drafts.app.noticeFor
import com.appthere.drafts.app.rememberOpenDocument
import com.appthere.drafts.app.rememberSessionDocument
import com.appthere.drafts.app.rememberUntitledDocument
import com.appthere.drafts.app.suggestedSaveName
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentState
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.Recovery
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.SnapshotTrigger
import com.appthere.drafts.platform.files.WindowRecord
import com.appthere.drafts.platform.files.chooseSaveLocation
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.files.desktopInstanceAddress
import com.appthere.drafts.platform.files.desktopSessionRoot
import com.appthere.drafts.platform.files.desktopSettingsRoot
import com.appthere.drafts.platform.files.epochMillis
import com.appthere.drafts.platform.files.prepareInstanceAddress
import com.appthere.drafts.platform.files.removeStaleInstance
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.intents.LaunchRequest
import com.appthere.drafts.platform.intents.SingleInstance
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Frame
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.Taskbar

/**
 * The desktop entry point.
 *
 * `appthere-drafts.md` 7.2: "Compose Desktop's `application { }` scope hosts multiple `Window`
 * composables. Maintain a `List<DocumentSession>` in the application state and emit one `Window`
 * per entry, each with its own `WindowState` (position, size, placement) persisted."
 *
 * The list is [SessionList]'s, so it survives the process. A path on the command line joins it; the
 * sessions from last time rejoin it on launch. With neither, an untitled document opens (7.4) --
 * see [sessionsAtLaunch].
 *
 * File > Open is still absent. The save dialog it would sit beside is in `:platform-files`.
 */
fun main(args: Array<String>) {
    // 9.4's single instance, one per user: the socket lives where only this user can reach it.
    val address = desktopInstanceAddress().also(::prepareInstanceAddress)
    val instance = SingleInstance(address, ::removeStaleInstance)
    val request = LaunchRequest.of(args.toList())
    val requests = Channel<LaunchRequest>(Channel.UNLIMITED)

    if (!becameTheRunningInstance(instance, request, requests)) return

    // Released when the last window closes, below, and also when the process is told to stop -- a
    // logout or shutdown sends SIGTERM, which ends the JVM without returning here. A socket left
    // behind is recovered by the next launch either way; this just does not leave one.
    Runtime.getRuntime().addShutdownHook(Thread(instance::release, "drafts-release-instance"))

    application { DraftsApplication(request, requests) }
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
 * system arbitrated rather than a guess about who started when. A launch that asked for nothing
 * hands over a new document: 7.4, "Launching while the application is already running opens a new
 * untitled document in a new window" -- the reader asked for the application again.
 */
private fun becameTheRunningInstance(
    instance: SingleInstance,
    request: LaunchRequest?,
    requests: Channel<LaunchRequest>,
): Boolean {
    if (!instance.claim { line -> LaunchRequest.decoded(line)?.let(requests::trySend) }) {
        instance.handOff((request ?: LaunchRequest.New()).encoded())
        return false
    }
    installOpenFileHandler(requests)
    installDockMenu(requests)
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
private fun installOpenFileHandler(requests: Channel<LaunchRequest>) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
            Desktop.getDesktop().setOpenFileHandler { event ->
                event.files.forEach { requests.trySend(LaunchRequest.Open(it.path)) }
            }
        }
    }
}

/**
 * 7.4 on macOS: the Dock menu's *New Markdown document* and *New Fountain screenplay*.
 *
 * `Taskbar`'s menu is the Dock's on macOS and unsupported elsewhere -- Linux has the `.desktop`
 * entry's actions instead, and Windows would have a jump list (`divergences.md`) -- so this is a
 * no-op there. Guarded like the open-file handler, for the same headless setups. Untested: there is
 * no Mac in this project's build environment.
 */
private fun installDockMenu(requests: Channel<LaunchRequest>) {
    runCatching {
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.MENU)) {
            Taskbar.getTaskbar().menu =
                PopupMenu().apply {
                    add(newDocumentItem(Strings.NEW_MARKDOWN, DocumentKind.Markdown, requests))
                    add(newDocumentItem(Strings.NEW_FOUNTAIN, DocumentKind.Fountain, requests))
                }
        }
    }
}

private fun newDocumentItem(
    label: String,
    kind: DocumentKind,
    requests: Channel<LaunchRequest>,
) = MenuItem(label).apply { addActionListener { requests.trySend(LaunchRequest.New(kind)) } }

/**
 * The application: 7.2's session list, one `Window` per entry.
 *
 * "Compose Desktop's `application { }` scope hosts multiple `Window` composables. Maintain a
 * `List<DocumentSession>` in the application state and emit one `Window` per entry, each with its
 * own `WindowState` (position, size, placement) persisted."
 */
@Composable
private fun ApplicationScope.DraftsApplication(
    request: LaunchRequest?,
    requests: Channel<LaunchRequest>,
) {
    val stores = remember { Stores.desktop() }
    val sessions = stores.sessions

    val open = remember { mutableStateListOf<SessionRecord>() }
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 8.3's pruning, before anything is restored: a session whose work reached a file more
        // than thirty days ago has no snapshot worth reopening.
        stores.snapshots.prune(epochMillis())

        open += sessionsAtLaunch(sessions, request, Strings.UNTITLED, stores.settings.kindForNew())
        request?.let { stores.rememberKindOf(it) }
        restored = true

        // Requests handed over by later launches, and by macOS: a file in a window of its own (9.4),
        // or a new document (7.4).
        for (next in requests) {
            open.serve(next, sessions, Strings.UNTITLED, stores.settings.kindForNew())
            stores.rememberKindOf(next)
        }
    }

    open.forEach { record ->
        // Keyed on the document, so dragging one window does not recompose another's.
        key(record.documentId) {
            DocumentWindow(
                record = record,
                stores = stores,
                // 7.4's Save As moves a document to a new file under the same id: the window stays,
                // and its title follows.
                onMove = { moved -> open.replaceAll { if (it.documentId == moved.documentId) moved else it } },
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
    // Once the launch's windows are open this goes, and closing the last of them ends the
    // application -- rather than conjuring another untitled document, which 7.4 asks for only at
    // launch.
    if (!restored) {
        Window(onCloseRequest = ::exitApplication, visible = false, title = Strings.WINDOW_TITLE) {}
    }
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
    stores: Stores,
    onMove: (SessionRecord) -> Unit,
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
            stores = stores,
            parent = window,
            onMove = onMove,
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
                stores.sessions.remember(record.documentId, geometry)
            }
    }

    // Closing runs to completion before the window goes: 8.1 lists window close among its triggers
    // and it is the one that gets no second chance. The composition is still alive here, which it
    // would not be if this ran after the window had gone.
    if (closed) {
        LaunchedEffect(Unit) {
            closing?.invoke()
            stores.sessions.closed(record.documentId)
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
    stores: Stores,
    parent: Frame,
    onMove: (SessionRecord) -> Unit,
    onReadyToClose: (suspend () -> Unit) -> Unit,
) {
    val identity = remember(record.documentId) { record.identity() }

    // 5.5's settings for this document's type, read before the window is drawn so the reader never
    // sees the defaults flash up and be replaced by their own typography.
    val saved by produceState<ReaderSettings?>(null, stores.settings, record.kind) {
        value = stores.settings.settingsFor(record.kind) ?: ReaderSettings()
    }

    // From the file, or -- untitled, or with a file that has vanished -- from its snapshot.
    val opening = rememberSessionDocument(stores.files, stores.snapshots, record)
    val moved by rememberUpdatedState(onMove)
    val saving = remember(stores) { SaveAs(stores.sessions) { moved(it) } }
    val kinds = remember(stores) { KindChange(stores.sessions, stores.settings) { moved(it) } }
    val scope = rememberCoroutineScope()

    when (opening) {
        is DocumentOpening.Opened -> {
            val keeper = remember(opening.document) { SnapshotKeeper(opening.document, stores.snapshots, identity) }

            onReadyToClose { keeper.snapshotOn(SnapshotTrigger.Closing) }

            saved?.let { initial ->
                DraftsApp(
                    document = opening.document,
                    initialSettings = initial,
                    keeper = keeper,
                    settingsStore = stores.settings,
                    kind = record.kind,
                    // Listed with the rest, though the window rather than the document answers it.
                    hostShortcuts = listOf(fullScreen),
                    saveAs = {
                        val suggested = opening.document.suggestedSaveName(record)

                        chooseSaveLocation(parent, Strings.SAVE_AS, suggested, near = record.accessToken)
                            ?.let { path -> saving.to(destinationOf(path, record), opening.document, record, keeper) }
                    },
                    // 7.4's kind, while there is no file whose extension already says.
                    onKindChange =
                        if (record.uri == null) {
                            { chosen -> scope.launch { kinds.to(chosen, record, keeper) } }
                        } else {
                            null
                        },
                )
            }
        }

        DocumentOpening.Opening -> {
            Notice(message = Strings.OPENING)
        }

        is DocumentOpening.Failed -> {
            Notice(message = noticeFor(opening.reason), name = record.displayName)
        }
    }
}

/**
 * The four places the desktop keeps things, made once and shared by every window: the reader's
 * files, 8.1's snapshots, 7.3's session list, and 5.5's settings.
 */
private class Stores(
    val files: PathDocumentStore,
    val snapshots: SnapshotStore,
    val sessions: SessionList,
    val settings: SettingsStore,
) {
    /**
     * A new document asked for by kind -- *New Fountain screenplay* -- is the reader creating that
     * kind, which makes it "the kind the reader last created" (7.4).
     */
    suspend fun rememberKindOf(request: LaunchRequest) {
        (request as? LaunchRequest.New)?.kind?.let { settings.rememberKindForNew(it) }
    }

    companion object {
        fun desktop(): Stores {
            val files = PathDocumentStore()
            val snapshots = SnapshotStore(files, desktopSessionRoot())
            return Stores(files, snapshots, SessionList(snapshots), SettingsStore(files, desktopSettingsRoot()))
        }
    }
}

/**
 * F11, or Ctrl+Cmd+F where that is the convention.
 *
 * Checked before the document sees it: full screen is a window operation, and a key the editor
 * might otherwise take is one the reader could not use to leave full screen again. The Mac's
 * chord is the one key in the application that needs Ctrl and ⌘ at once, which [Shortcut] does not
 * describe; the list says it in words instead.
 */
private val fullScreen = Shortcut(Strings.SHORTCUT_FULL_SCREEN, Key.F11, "F11")

private fun togglesFullScreen(event: KeyEvent): Boolean =
    fullScreen.matches(event) ||
        (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.isMetaPressed && event.key == Key.F)

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
