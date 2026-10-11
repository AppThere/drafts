package com.appthere.drafts.app.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.appthere.drafts.app.OpenDocument
import com.appthere.drafts.app.ProjectActions
import com.appthere.drafts.app.ProjectList
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
import com.appthere.drafts.platform.files.PathFolderEditing
import com.appthere.drafts.platform.files.PathFolderListing
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
import com.appthere.drafts.platform.files.desktopProjectRoot
import com.appthere.drafts.platform.files.desktopSessionRoot
import com.appthere.drafts.platform.files.desktopSettingsRoot
import com.appthere.drafts.platform.files.epochMillis
import com.appthere.drafts.platform.files.prepareInstanceAddress
import com.appthere.drafts.platform.files.removeStaleInstance
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.intents.LaunchRequest
import com.appthere.drafts.platform.intents.SingleInstance
import com.appthere.drafts.platform.windows.SessionList
import com.appthere.drafts.project.ProjectFiles
import com.appthere.drafts.project.ProjectSession
import com.appthere.drafts.project.ProjectSessions
import com.appthere.drafts.project.ProjectStateStore
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
import java.util.concurrent.ConcurrentHashMap

/**
 * One document, in one window, remembering where it was put.
 *
 * The geometry is written back through [SessionList] rather than held in memory, because 7.3 asks
 * for it to survive the process. It is debounced: dragging a window emits a position per frame, and
 * a write per frame would put the reader's own disk under the thing they are dragging.
 */
@Composable
internal fun ApplicationScope.DocumentWindow(
    record: SessionRecord,
    stores: Stores,
    onMove: (SessionRecord) -> Unit,
    onClose: () -> Unit,
    windows: (LaunchRequest) -> Unit,
    project: DocumentInProject = DocumentInProject(),
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
        // `projects/project-windows`: a document from a project says which, and where in it.
        title = project.title(record.displayName),
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
            windows = windows,
            projects = project.actions,
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
internal fun FileDocument(
    record: SessionRecord,
    stores: Stores,
    parent: Frame,
    onMove: (SessionRecord) -> Unit,
    onReadyToClose: (suspend () -> Unit) -> Unit,
    windows: (LaunchRequest) -> Unit,
    projects: ProjectActions? = null,
) {
    val identity = remember(record.documentId) { record.identity() }

    // 5.5's settings for this document's type, read before the window is drawn so the reader never
    // sees the defaults flash up and be replaced by their own typography.
    val saved by produceState<ReaderSettings?>(null, stores.settings, record.kind) {
        value = stores.settings.settingsFor(record.kind) ?: ReaderSettings()
    }

    // From the file, or -- untitled, or with a file that has vanished -- from its snapshot.
    val opening = rememberSessionDocument(stores.files, stores.snapshots, record, stores.settings)
    val moved by rememberUpdatedState(onMove)
    val saving = remember(stores) { SaveAs(stores.sessions, stores.settings) { moved(it) } }
    val kinds = remember(stores) { KindChange(stores.sessions, stores.settings) { moved(it) } }
    val scope = rememberCoroutineScope()

    when (opening) {
        is DocumentOpening.Opened -> {
            val keeper = remember(opening.document) { SnapshotKeeper(opening.document, stores.snapshots, identity) }

            Registered(stores, record.uri, opening.document)

            onReadyToClose { keeper.snapshotOn(SnapshotTrigger.Closing) }

            saved?.let { initial ->
                DraftsApp(
                    document = opening.document,
                    initialSettings = initial,
                    keeper = keeper,
                    settingsStore = stores.settings,
                    kind = record.kind,
                    // Full screen is listed with the rest, though the window answers it. New and Open
                    // are requests like any other launch's: each a window of its own (7.1, 9.4).
                    host =
                        HostActions(
                            shortcuts = listOf(fullScreen),
                            projects = projects,
                            newDocument = { windows(LaunchRequest.New()) },
                            openDocument = {
                                scope.launch {
                                    val title = getString(Res.string.open_document_title)
                                    chooseFileToOpen(parent, title, openable, near = record.accessToken)
                                        ?.let { windows(LaunchRequest.Open(it)) }
                                }
                            },
                        ),
                    saveAs = {
                        val suggested =
                            opening.document.suggestedSaveName(
                                record,
                                untitled = getString(Res.string.untitled),
                                myVersion = getString(Res.string.my_version),
                            )

                        chooseSaveLocation(parent, getString(Res.string.save_as), suggested, near = record.accessToken)
                            ?.let { path -> saving.to(destinationOf(path, record), opening.document, record, keeper) }
                    },
                    // 7.4's kind, while there is no file whose extension already says.
                    onKindChange =
                        if (record.uri == null) {
                            { chosen -> scope.launch { kinds.to(chosen, record, keeper, opening.document.editor) } }
                        } else {
                            null
                        },
                )
            }
        }

        DocumentOpening.Opening -> {
            Notice(message = stringResource(Res.string.opening))
        }

        is DocumentOpening.Failed -> {
            Notice(message = noticeFor(opening.reason), name = record.displayName)
        }
    }
}

/**
 * [document] known to the projects while it is on screen, by its [file], so that putting the file
 * in the trash can ask first if its words are not saved (`projects/trash`).
 */
@Composable
internal fun Registered(
    stores: Stores,
    file: String?,
    document: OpenDocument,
) {
    file ?: return
    DisposableEffect(document, file) {
        stores.openDocuments[file] = document
        onDispose { stores.openDocuments.remove(file, document) }
    }
}

/**
 * The places the desktop keeps things, made once and shared by every window: the reader's files,
 * 8.1's snapshots, 7.3's session list, 5.5's settings, and the projects.
 */
internal class Stores(
    val files: PathDocumentStore,
    val snapshots: SnapshotStore,
    val sessions: SessionList,
    val settings: SettingsStore,
) {
    /** The projects this device has opened, and the open ones' sessions -- one per project. */
    val projects = ProjectList(files, desktopProjectRoot())
    val projectSessions =
        ProjectSessions { location ->
            ProjectSession(
                location,
                ProjectFiles(PathFolderListing(), PathFolderEditing(), files),
                ProjectStateStore(files, desktopProjectRoot()),
            )
        }

    /** Every document open in a window or a project's editor, by file: what can still be unsaved. */
    val openDocuments = ConcurrentHashMap<String, OpenDocument>()

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
internal val fullScreen = Shortcut(Res.string.shortcut_full_screen, Key.F11, "F11")

/** What Open offers: every extension 9.1 says is a document this application reads. */
private val openable = DocumentKind.entries.flatMap { it.extensions }

internal fun togglesFullScreen(event: KeyEvent): Boolean =
    fullScreen.matches(event) ||
        (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.isMetaPressed && event.key == Key.F)

/** 7.3's `window` record: "x, y, width, height, placement". */
internal fun WindowState.geometry() =
    WindowRecord(
        x = position.x.value.toInt(),
        y = position.y.value.toInt(),
        width = size.width.value.toInt(),
        height = size.height.value.toInt(),
        placement = placement.name,
    )

/** Unknown or unrecognised placements come back floating, which is what a new window would be. */
internal fun placementOf(name: String?): WindowPlacement =
    WindowPlacement.entries.firstOrNull { it.name == name } ?: WindowPlacement.Floating

internal val defaultWidth = 900.dp
internal val defaultHeight = 1100.dp

/** Long enough that dragging a window is one write rather than one per frame. */
internal const val SETTLE_MILLIS = 400L
