package com.appthere.drafts.app.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.appthere.drafts.app.Notice
import com.appthere.drafts.app.ProjectActions
import com.appthere.drafts.app.ProjectController
import com.appthere.drafts.app.ProjectHost
import com.appthere.drafts.app.ProjectWindow
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.no_document
import com.appthere.drafts.i18n.resources.opening
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderChange
import com.appthere.drafts.platform.files.SafDocumentStore
import com.appthere.drafts.platform.files.SafFolderWatching
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.project.ProjectLocation
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * One project, in its own task (`projects/project-windows`): the binder beside an editor pane, the
 * binder a drawer on a phone. One task per project -- `documentLaunchMode="intoExisting"` keys it on
 * the folder -- so opening a project that is open brings it forward.
 *
 * Reconciled in full when it opens, again whenever it is resumed -- the guarantee on Android, where
 * providers announce changes inconsistently -- and whenever the provider does announce one.
 */
class ProjectActivity : ComponentActivity() {
    private var controller: ProjectController? = null
    private var closing: (suspend () -> Unit)? = null
    private var location: ProjectLocation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        location = projectOf(intent)
        setContent {
            DraftsTheme(ReaderSettings()) {
                location?.let { Project(it) } ?: Notice(message = stringResource(Res.string.no_document))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller?.reconcile()
    }

    /** The project closed: the document in its editor snapshotted and closed, and the project marked closed. */
    override fun onStop() {
        super.onStop()
        if (!isFinishing || isChangingConfigurations) return
        val project = location ?: return
        val work = closing
        val app = applicationContext
        closingScope.launch {
            work?.invoke()
            AndroidProjects.list(app).closed(project.ref)
            AndroidProjects.sessions(app).close(project.ref)
        }
    }

    @Composable
    private fun Project(location: ProjectLocation) {
        val scope = rememberCoroutineScope()
        val storage = remember { Storage(this) }
        val documents = remember { SafDocumentStore(this) }
        val list = remember { AndroidProjects.list(this) }
        val session by produceState<ProjectSession?>(null, location) {
            value =
                AndroidProjects.sessions(this@ProjectActivity).sessionFor(location)
        }
        var pane by remember { mutableStateOf<SessionRecord?>(null) }

        val current = session
        if (current == null) {
            Notice(message = stringResource(Res.string.opening))
            return
        }
        val host =
            remember(current) {
                AndroidProjectHost(this, location) { record ->
                    // The document leaving the editor is closed as a window would be: snapshot first.
                    val leaving = closing
                    scope.launch { leaving?.invoke() }
                    pane = record
                }
            }
        val controller = remember(current) { ProjectController(current, scope, host).also { this.controller = it } }

        ProjectWindow(controller) {
            run {
                pane?.let { record ->
                    DocumentSession(
                        open = record,
                        storage = storage,
                        documents = documents,
                        onMove = { pane = it },
                        onClosing = { closing = it },
                        projects = projectActions(),
                    )
                } ?: Notice(message = stringResource(Res.string.no_document))
            }
        }

        LaunchedEffect(controller) {
            // What the window showed last time, and the document that was in its editor.
            val kept = list.open().firstOrNull { it.token == location.ref.token }
            controller.expanded(
                kept
                    ?.expanded
                    .orEmpty()
                    .map(::ProjectPath)
                    .toSet(),
            )
            controller.select(kept?.selected?.let(::ProjectPath))
            kept?.document?.let { id -> pane = reopened(storage.sessions, storage.snapshots, id) }
            list.opened(location.ref, location.name)
            controller.reconcile(full = true)
            SafFolderWatching(this@ProjectActivity).changes(location.ref).collect { change ->
                controller.reconcile(full = change == FolderChange.Lost)
            }
        }
        LaunchedEffect(controller) {
            snapshotFlow { Triple(controller.ui.value.expanded, controller.ui.value.selected, pane?.documentId) }
                .distinctUntilChanged()
                .collectLatest { (expanded, selected, document) ->
                    delay(SETTLE_MILLIS)
                    list.remember(location.ref, expanded.map { it.value }, selected?.value, document)
                }
        }
    }

    /** The ways to other projects, from the document in this project's editor. */
    private fun projectActions() =
        ProjectActions(
            openFolder = {
                startActivity(Intent(this, ProjectsActivity::class.java).putExtra(ProjectsActivity.EXTRA_CHOOSE, true))
            },
            recent = { startActivity(Intent(this, ProjectsActivity::class.java)) },
        )

    private companion object {
        /** Long enough that expanding several folders in a row is one write. */
        const val SETTLE_MILLIS = 400L
    }
}

/**
 * What the binder asks of Android: a document into this project's editor, or into a task of its own
 * beside it, carrying its project so its window can say where it is; anything else to the system.
 */
private class AndroidProjectHost(
    private val activity: ComponentActivity,
    private val project: ProjectLocation,
    private val intoEditor: (SessionRecord) -> Unit,
) : ProjectHost {
    override fun openDocument(
        document: ProjectNode.Document,
        newWindow: Boolean,
    ) {
        val uri = Uri.parse(document.ref.token)
        if (newWindow) {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, uri, activity, DocumentActivity::class.java)
                    .putExtra(DocumentActivity.EXTRA_PROJECT, project.ref.token)
                    .putExtra(DocumentActivity.EXTRA_PROJECT_NAME, project.name)
                    .putExtra(DocumentActivity.EXTRA_PROJECT_FOLDER, document.path.parent.value)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    ),
            )
            return
        }
        val storage = Storage(activity)
        closingScope.launch {
            projectDocumentIdentity(activity, uri, document.kind.id)?.let { identity ->
                val record = storage.sessions.opened(identity)
                activity.runOnUiThread { intoEditor(record) }
            }
        }
    }

    override fun openExternally(file: DocumentRef) {
        val uri = Uri.parse(file.token)
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, activity.contentResolver.getType(uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun hasUnsavedEdits(file: DocumentRef): Boolean =
        AndroidProjects.openDocuments[file.token]?.lifecycle?.hasUnsavedEdits == true
}
