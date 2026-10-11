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
import com.appthere.drafts.app.HostActions
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
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.my_version
import com.appthere.drafts.i18n.resources.new_fountain
import com.appthere.drafts.i18n.resources.new_markdown
import com.appthere.drafts.i18n.resources.open_document_title
import com.appthere.drafts.i18n.resources.opening
import com.appthere.drafts.i18n.resources.save_as
import com.appthere.drafts.i18n.resources.shortcut_full_screen
import com.appthere.drafts.i18n.resources.untitled
import com.appthere.drafts.i18n.resources.window_title
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
import com.appthere.drafts.platform.files.chooseFileToOpen
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
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
 * New and Open are in each window's reader controls and on Ctrl+N and Ctrl+O: requests on the same
 * channel as every other launch's, so each is a window of its own.
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
 *
 * Suspends because its labels do: 11.1 puts them in resources, and a resource read outside a
 * composition is a suspending one.
 */
private suspend fun installDockMenu(requests: Channel<LaunchRequest>) {
    val markdown = getString(Res.string.new_markdown)
    val fountain = getString(Res.string.new_fountain)

    runCatching {
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.MENU)) {
            Taskbar.getTaskbar().menu =
                PopupMenu().apply {
                    add(newDocumentItem(markdown, DocumentKind.Markdown, requests))
                    add(newDocumentItem(fountain, DocumentKind.Fountain, requests))
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
    val projects = remember { DesktopProjects(stores) }
    val scope = rememberCoroutineScope()

    val open = remember { mutableStateListOf<SessionRecord>() }
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 7.4's Dock menu. Here rather than beside the open-file handler because its two labels are
        // 11.1 resources and reading one suspends; nothing is waiting for the menu, and the Dock
        // has it before anybody has reached for it.
        installDockMenu(requests)

        // 8.3's pruning, before anything is restored: a session whose work reached a file more
        // than thirty days ago has no snapshot worth reopening.
        stores.snapshots.prune(epochMillis())

        open += atLaunch(request, stores, projects)
        request?.let { stores.rememberKindOf(it) }
        restored = true
        (request as? LaunchRequest.Open)?.let { projects.offerFor(it.path) }

        // Requests handed over by later launches, and by macOS: a file in a window of its own (9.4),
        // or a new document (7.4).
        for (next in requests) {
            open.serve(next, sessions, getString(Res.string.untitled), stores.settings.kindForNew())
            stores.rememberKindOf(next)
            (next as? LaunchRequest.Open)?.let { projects.offerFor(it.path) }
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
                windows = { requests.trySend(it) },
                project = projects.actionsFor(record.uri, { null }) { work -> scope.launch { work() } },
            )
        }
    }

    projects.open.forEach { entry ->
        key(entry.token) { ProjectWindowFor(entry, stores, projects, windows = { requests.trySend(it) }) }
    }
    if (projects.home) ProjectsWindow(stores, projects)

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
        Window(onCloseRequest = ::exitApplication, visible = false, title = stringResource(Res.string.window_title)) {}
    }
}

/**
 * What this launch opens: projects first, so that a document in a project window's editor comes
 * back there and not in a window of its own (`projects/project-windows`), then 7.3's sessions and
 * whatever the launch asked for.
 */
private suspend fun atLaunch(
    request: LaunchRequest?,
    stores: Stores,
    projects: DesktopProjects,
): List<SessionRecord> {
    val projectsRestored = projects.restore().isNotEmpty()
    val held = projects.heldDocuments()
    val untitled = getString(Res.string.untitled)
    return sessionsAtLaunch(
        stores.sessions,
        request,
        untitled,
        stores.settings.kindForNew(),
        projectsRestored,
    ).filterNot {
        it.documentId in
            held
    }
}
