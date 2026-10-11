package com.appthere.drafts.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.appthere.drafts.app.NewProjectPrompt
import com.appthere.drafts.app.Notice
import com.appthere.drafts.app.Problem
import com.appthere.drafts.app.ProjectController
import com.appthere.drafts.app.ProjectEntry
import com.appthere.drafts.app.ProjectHost
import com.appthere.drafts.app.ProjectWindow
import com.appthere.drafts.app.ProjectsHomeHost
import com.appthere.drafts.app.ProjectsScreen
import com.appthere.drafts.app.problemText
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.no_document
import com.appthere.drafts.i18n.resources.opening
import com.appthere.drafts.i18n.resources.recent_projects
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderChange
import com.appthere.drafts.platform.files.PathFolderWatching
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.files.isFolder
import com.appthere.drafts.platform.files.openWithSystem
import com.appthere.drafts.platform.intents.LaunchRequest
import com.appthere.drafts.project.Outcome
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * One project, in one window (`projects/project-windows`): the binder beside an editor that shows
 * the document the writer opened from it.
 *
 * Reconciled in full when it opens, then again whenever the window gains focus and whenever the
 * folder changes (`projects/reconciliation`, "Reconciliation runs on open, on focus and on change"),
 * all of it off the main thread through the project's session. What the window shows -- the folders
 * open, the item chosen, the document in the editor -- is kept as it changes, so it comes back.
 */
@Composable
internal fun ApplicationScope.ProjectWindowFor(
    entry: ProjectEntry,
    stores: Stores,
    projects: DesktopProjects,
    windows: (LaunchRequest) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberWindowState(size = DpSize(projectWidth, projectHeight))
    val session by produceState<ProjectSession?>(null, entry.token) {
        value = stores.projectSessions.sessionFor(projects.location(entry))
    }
    var pane by remember { mutableStateOf<SessionRecord?>(null) }
    var closing by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var closed by remember { mutableStateOf(false) }

    Window(onCloseRequest = { closed = true }, state = state, title = entry.name) {
        val current = session
        val host =
            remember(current) {
                DesktopProjectHost(stores, windows) { document ->
                    scope.launch {
                        closing?.invoke()
                        pane?.let { stores.sessions.closed(it.documentId) }
                        val opened = stores.sessions.opened(desktopIdentity(document.ref.token, document.kind.id))
                        pane = opened
                        projects.holding(entry, opened.documentId)
                    }
                }
            }
        val controller = current?.let { remember(it) { ProjectController(it, scope, host) } }

        DraftsTheme(ReaderSettings()) {
            if (controller == null) {
                Notice(message = stringResource(Res.string.opening))
            } else {
                ProjectWindow(controller) {
                    Box {
                        pane?.let { record ->
                            FileDocument(
                                record = record,
                                stores = stores,
                                parent = window,
                                onMove = { pane = it },
                                onReadyToClose = { closing = it },
                                windows = windows,
                                projects =
                                    projects
                                        .actionsFor(
                                            record.uri,
                                            { window },
                                        ) { work -> scope.launch { work() } }
                                        .actions,
                            )
                        } ?: Notice(message = stringResource(Res.string.no_document))
                    }
                }
                Live(controller, entry, stores, projects)
                Focused(window) { controller.reconcile() }
            }
        }

        LaunchedEffect(projects.forward) {
            if (projects.forward == entry.token) {
                window.toFront()
                projects.forward = null
            }
        }
    }

    // The document the window had last time, back in its editor.
    LaunchedEffect(entry.token) {
        entry.document?.let { id ->
            stores.snapshots.recordOf(id)?.let { pane = stores.sessions.opened(it.identity()) }
        }
    }

    if (closed) {
        LaunchedEffect(Unit) {
            closing?.invoke()
            pane?.let { stores.sessions.closed(it.documentId) }
            projects.closed(entry)
        }
    }
}

/**
 * The project kept current, and its window's state kept: a full look on opening, a look on every
 * change the platform reports -- in full if it lost track -- and the binder's state written back as
 * it settles.
 */
@Composable
private fun Live(
    controller: ProjectController,
    entry: ProjectEntry,
    stores: Stores,
    projects: DesktopProjects,
) {
    LaunchedEffect(controller) {
        controller.expanded(entry.expanded.map(::ProjectPath).toSet())
        controller.select(entry.selected?.let(::ProjectPath))
        controller.reconcile(full = true)
        PathFolderWatching().changes(DocumentRef(entry.token)).collect { change ->
            controller.reconcile(
                full =
                    change == FolderChange.Lost,
            )
        }
    }
    LaunchedEffect(controller) {
        snapshotFlow { controller.ui.value.expanded to controller.ui.value.selected }
            .distinctUntilChanged()
            .collectLatest { (expanded, selected) ->
                delay(SETTLE_MILLIS)
                val document = projects.open.firstOrNull { it.token == entry.token }?.document
                stores.projects.remember(DocumentRef(entry.token), expanded.map { it.value }, selected?.value, document)
            }
    }
}

/** [onFocus] whenever [window] gains focus: the writer coming back from somewhere else. */
@Composable
private fun Focused(
    window: java.awt.Window,
    onFocus: () -> Unit,
) {
    val focused by rememberUpdatedState(onFocus)
    DisposableEffect(window) {
        val listener =
            object : WindowAdapter() {
                override fun windowGainedFocus(event: WindowEvent) = focused()
            }
        window.addWindowFocusListener(listener)
        onDispose { window.removeWindowFocusListener(listener) }
    }
}

/** What the binder asks of the desktop: documents into the editor or a window, other files to the system. */
private class DesktopProjectHost(
    private val stores: Stores,
    private val windows: (LaunchRequest) -> Unit,
    private val intoEditor: (ProjectNode.Document) -> Unit,
) : ProjectHost {
    override fun openDocument(
        document: ProjectNode.Document,
        newWindow: Boolean,
    ) = if (newWindow) windows(LaunchRequest.Open(document.ref.token)) else intoEditor(document)

    override fun openExternally(file: DocumentRef) {
        openWithSystem(file.token)
    }

    override fun hasUnsavedEdits(file: DocumentRef): Boolean =
        stores.openDocuments[file.token]?.lifecycle?.hasUnsavedEdits == true
}

/**
 * The writer's projects, in a window of their own: recent ones, a folder to open, and a new one to
 * make from a template.
 */
@Composable
internal fun ApplicationScope.ProjectsWindow(
    stores: Stores,
    projects: DesktopProjects,
) {
    val scope = rememberCoroutineScope()
    var refreshed by remember { mutableIntStateOf(0) }
    val entries by produceState(emptyList<ProjectEntry>(), refreshed, projects.open.size) {
        value =
            stores.projects.entries()
    }
    var problem by remember { mutableStateOf<Outcome?>(null) }

    Window(
        onCloseRequest = { projects.home = false },
        title = stringResource(Res.string.recent_projects),
        state = rememberWindowState(size = DpSize(homeWidth, homeHeight)),
    ) {
        val host = remember { DesktopProjectsHome(scope, projects, window) { refreshed++ } }

        DraftsTheme(ReaderSettings()) {
            ProjectsScreen(
                entries = entries,
                host = host,
                creating = projects.newIn != null,
                onCreate = { name, template ->
                    scope.launch {
                        problem =
                            projects.create(name, template).outcome.takeIf { it != Outcome.Done }
                    }
                },
                onCancel = { projects.newIn = null },
                problem = problem,
            )
        }
    }
}

/** The projects window's answers: each runs off the window, and the list is read again after. */
private class DesktopProjectsHome(
    private val scope: CoroutineScope,
    private val projects: DesktopProjects,
    private val window: Frame,
    private val refresh: () -> Unit,
) : ProjectsHomeHost {
    override fun open(entry: ProjectEntry) = run { projects.open(entry.token, entry.name) }

    override fun chooseAgain(entry: ProjectEntry) =
        run {
            projects.forget(entry)
            projects.openFolder(window)
        }

    override fun pin(
        entry: ProjectEntry,
        pinned: Boolean,
    ) = run { projects.pin(entry, pinned) }

    override fun remove(entry: ProjectEntry) = run { projects.forget(entry) }

    override fun openFolder() = run { projects.openFolder(window) }

    override fun newProject() = run { projects.chooseNewProjectFolder(window) }

    override fun isAvailable(entry: ProjectEntry): Boolean = isFolder(entry.token)

    private fun run(work: suspend () -> Unit) {
        scope.launch {
            work()
            refresh()
        }
    }
}

private val projectWidth = 1280.dp
private val projectHeight = 900.dp
private val homeWidth = 560.dp
private val homeHeight = 640.dp
