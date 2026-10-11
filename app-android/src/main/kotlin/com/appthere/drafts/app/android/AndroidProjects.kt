package com.appthere.drafts.app.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.appthere.drafts.app.OpenDocument
import com.appthere.drafts.app.ProjectEntry
import com.appthere.drafts.app.ProjectList
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SafDocumentStore
import com.appthere.drafts.platform.files.SafFolderEditing
import com.appthere.drafts.platform.files.SafFolderListing
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.androidIdentity
import com.appthere.drafts.platform.files.androidProjectRoot
import com.appthere.drafts.platform.files.displayNameOf
import com.appthere.drafts.platform.files.resolveAndroidToken
import com.appthere.drafts.project.ProjectFiles
import com.appthere.drafts.project.ProjectLocation
import com.appthere.drafts.project.ProjectSession
import com.appthere.drafts.project.ProjectSessions
import com.appthere.drafts.project.ProjectStateStore
import java.util.concurrent.ConcurrentHashMap

/**
 * What this process knows about projects (`projects-core` design D1, D9): one session per project
 * whichever activity shows it, the list of recent and open projects, and every document open in a
 * task or a project's editor, by file -- what can still have unsaved words.
 *
 * In the process, like [OpenDocuments]: Android's activities share it, and a project window and a
 * document window opened from it must share one session or each would report the other's changes as
 * someone else's.
 */
internal object AndroidProjects {
    private var shared: ProjectSessions? = null

    /** Every document on screen in this process, by its `content://` URI. */
    val openDocuments = ConcurrentHashMap<String, OpenDocument>()

    @Synchronized
    fun sessions(context: Context): ProjectSessions {
        val app = context.applicationContext
        return shared ?: ProjectSessions { location ->
            ProjectSession(
                location,
                ProjectFiles(SafFolderListing(app), SafFolderEditing(app), SafDocumentStore(app)),
                ProjectStateStore(PathDocumentStore(), androidProjectRoot(app)),
            )
        }.also { shared = it }
    }

    fun list(context: Context) = ProjectList(PathDocumentStore(), androidProjectRoot(context.applicationContext))

    /** Whether [entry]'s folder can still be reached: its grant, or its parent's, is still held. */
    fun isAvailable(
        context: Context,
        entry: ProjectEntry,
    ): Boolean = resolveAndroidToken(context, entry.token) != null
}

/**
 * A project's own task: keyed on the folder's URI, so `documentLaunchMode="intoExisting"` brings an
 * open project forward instead of opening a second window on it.
 */
internal fun projectIntent(
    context: Context,
    location: ProjectLocation,
): Intent =
    Intent(context, ProjectActivity::class.java)
        .setData(Uri.parse(location.ref.token))
        .putExtra(EXTRA_PROJECT_NAME, location.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)

/** The project a [projectIntent] names. */
internal fun projectOf(intent: Intent): ProjectLocation? =
    intent.data?.let {
        ProjectLocation(
            DocumentRef(it.toString()),
            intent.getStringExtra(EXTRA_PROJECT_NAME) ?: it.lastPathSegment.orEmpty(),
        )
    }

/**
 * A folder the reader chose with the system's folder picker, kept: the grant persisted, so the
 * project opens again after a restart without being chosen again, and its name read.
 */
internal fun keptFolder(
    context: Context,
    tree: Uri,
): ProjectLocation? =
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val top = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ProjectLocation(
            DocumentRef(tree.toString()),
            displayNameOf(context.contentResolver, top) ?: tree.lastPathSegment.orEmpty(),
        )
    }.getOrNull()

/**
 * The identity of a document opened from a project's binder. Its grant is the folder's, not its
 * own, so the token is its own URI whenever the folder's grant covers it -- which is what lets it
 * come back after a restart (`projects-core` design D10).
 */
internal fun projectDocumentIdentity(
    context: Context,
    uri: Uri,
    kind: String,
): SessionIdentity? =
    androidIdentity(context, uri, kind)?.let { identity ->
        if (identity.accessToken == null &&
            resolveAndroidToken(context, uri.toString()) != null
        ) {
            identity.copy(accessToken = uri.toString())
        } else {
            identity
        }
    }

/**
 * The project a document opened from outside belongs to, if it is under the folder of one this
 * device has opened (`projects/project-windows`, "Opening a file inside a project offers the
 * project"). Asked of the document's provider through each project's own grant: a provider refuses
 * to describe, through a tree, a document that is not in it. Without a grant there is no way to tell,
 * and nothing is offered.
 *
 * A project that is a folder inside the granted one is matched by its document id being a prefix
 * of the file's, which is how path-shaped ids -- Android's own storage, most providers -- nest.
 */
internal suspend fun enclosingProject(
    context: Context,
    document: Uri,
): ProjectEntry? {
    val id = runCatching { DocumentsContract.getDocumentId(document) }.getOrNull() ?: return null
    return AndroidProjects.list(context).entries().firstOrNull { entry ->
        runCatching {
            val project = Uri.parse(entry.token)
            val tree =
                DocumentsContract.buildTreeDocumentUri(
                    project.authority,
                    DocumentsContract.getTreeDocumentId(project),
                )
            val top =
                if (DocumentsContract.isDocumentUri(
                        context,
                        project,
                    )
                ) {
                    DocumentsContract.getDocumentId(project)
                } else {
                    DocumentsContract.getTreeDocumentId(project)
                }
            val through = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            project.authority == document.authority && id.startsWith("$top/") &&
                displayNameOf(context.contentResolver, through) != null
        }.getOrDefault(false)
    }
}

private const val EXTRA_PROJECT_NAME = "com.appthere.drafts.PROJECT_NAME"
