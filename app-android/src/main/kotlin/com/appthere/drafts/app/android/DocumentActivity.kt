package com.appthere.drafts.app.android

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.appthere.drafts.app.DocumentOpening
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.app.HostActions
import com.appthere.drafts.app.KindChange
import com.appthere.drafts.app.Notice
import com.appthere.drafts.app.ProjectActions
import com.appthere.drafts.app.SaveAs
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.kindOf
import com.appthere.drafts.app.noticeFor
import com.appthere.drafts.app.rememberSessionDocument
import com.appthere.drafts.app.suggestedSaveName
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.breadcrumb
import com.appthere.drafts.i18n.resources.cancel
import com.appthere.drafts.i18n.resources.document_gone
import com.appthere.drafts.i18n.resources.document_in_project
import com.appthere.drafts.i18n.resources.document_in_project_title
import com.appthere.drafts.i18n.resources.my_version
import com.appthere.drafts.i18n.resources.no_document
import com.appthere.drafts.i18n.resources.not_a_text_document
import com.appthere.drafts.i18n.resources.open_project
import com.appthere.drafts.i18n.resources.opening
import com.appthere.drafts.i18n.resources.untitled
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.SafDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.androidIdentity
import com.appthere.drafts.platform.files.displayNameOf
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.project.ProjectLocation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * One document, in one task.
 *
 * `appthere-drafts.md` 7.2 on Android: "Launch each document with `FLAG_ACTIVITY_NEW_DOCUMENT or
 * FLAG_ACTIVITY_MULTIPLE_TASK`. Each document then appears as its own entry in Recents and can be
 * dragged into split-screen against another instance of the app -- which is what makes side-by-side
 * work on tablets, foldables, and ChromeOS."
 *
 * It is reached two ways, and both end in the same session record:
 *
 * - From outside, as 9.2's VIEW, EDIT or SEND, carrying a `content://` URI. The grant is persisted
 *   at that moment (7.3) and the document joins the session list.
 * - From [LauncherActivity], carrying a session id this application already knows -- a document
 *   being restored (7.3), or an untitled one just created (7.4), which has no URI to be named by.
 *
 * Recents shows the document's name rather than the application's: a column of identical entries is
 * not a document switcher.
 */
class DocumentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shown = intent
        setContent { Document(shown) }
    }

    /**
     * A later launch for a document this task is already showing.
     *
     * `documentLaunchMode="intoExisting"` keys the task on the intent's data, so the intent that
     * arrives here is for the same document and the words on screen are already the right ones.
     * What it must not do is nothing at all: `getIntent()` would go on returning the first one, so
     * anything asking later what this task is about would get a stale answer.
     *
     * Observed rather than reasoned about -- opening the same file twice on a device reported
     * "intent has been delivered to currently running top-most instance" and nothing happened.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shown = intent
    }

    /**
     * The reader closed this document: Back out of it, or swiped its task away.
     *
     * `onStop` rather than `onDestroy`, because the work is asked for here and carried out
     * afterwards (see [closingScope]), and this is the last moment at which the process is
     * certainly not a cached one. `isFinishing` is what separates closing the document from merely
     * leaving it on screen; `isChangingConfigurations` catches the configuration changes the
     * manifest does not already absorb, where the Activity is destroyed and immediately rebuilt.
     */
    override fun onStop() {
        super.onStop()
        if (isFinishing && !isChangingConfigurations) close()
    }

    /** 7.4's "already running" question is asked of the process, so this task stops answering it. */
    override fun onDestroy() {
        super.onDestroy()
        registered?.let(OpenDocuments::closed)
    }

    /** Runs the closing work once, then forgets it, so a second lifecycle callback does nothing. */
    private fun close() {
        val work = closing ?: return
        closing = null
        closingScope.launch { work() }
    }

    /** The intent this task is about, which a later launch can replace. */
    private var shown: Intent? by mutableStateOf(null)

    /** The document this task told [OpenDocuments] about, so it can take it back. */
    private var registered: String? = null

    /**
     * What to do when this document is closed, as the composition that knows how hands it over.
     *
     * The keeper and the session list both live inside the composition, and the moment they are
     * needed is a lifecycle callback outside it. The desktop has the same seam and the same name.
     */
    private var closing: (suspend () -> Unit)? = null

    @Composable
    private fun Document(intent: Intent?) {
        if (intent == null) {
            Notice(message = stringResource(Res.string.no_document))
            return
        }

        val storage = remember { Storage(this) }

        // The reader's own files go through the Storage Access Framework; everything of ours goes
        // through `storage`. This one needs the Activity, because the grants are the Activity's.
        val documents = remember { SafDocumentStore(this) }

        // Mutable, not derived: *Save As* and 7.4's choice of kind both move the document, and what
        // the chrome shows afterwards is the record they moved it to.
        var record by remember(intent) { mutableStateOf<SessionRecord?>(null) }
        var looked by remember(intent) { mutableStateOf(false) }

        LaunchedEffect(intent) {
            record = recordFor(intent, storage)
            looked = true
            record?.let {
                OpenDocuments.opened(it.documentId)
                registered = it.documentId
            }
        }

        when (val open = record) {
            null -> {
                Notice(message = if (looked) nothingToShow(intent) else stringResource(Res.string.opening))
            }

            else -> {
                val title = titleOf(open.displayName)
                DocumentSession(
                    open = open,
                    storage = storage,
                    documents = documents,
                    onMove = { moved -> record = moved },
                    onClosing = { closing = it },
                    onTitle = { describeTask(title) },
                    projects = projectActions(intent),
                )
                OfferProject(open)
            }
        }
    }

    /**
     * Why there is nothing to show, in the reader's terms rather than the intent's.
     *
     * One of our own sessions that is not there is a document that has been discarded (7.4) and
     * whose card in Recents outlived it. A document URI that produced nothing is a handover 9.2's
     * permissive filters let in and `looksLikeText` turned away -- or, rarely, one whose grant
     * could not be taken, which from the reader's side is the same sentence: it did not open. An
     * intent carrying no document at all is a share that brought nothing.
     */
    @Composable
    private fun nothingToShow(intent: Intent): String =
        when {
            sessionIdOf(intent) != null -> stringResource(Res.string.document_gone)
            documentUri(intent) != null -> stringResource(Res.string.not_a_text_document)
            else -> stringResource(Res.string.no_document)
        }

    /**
     * The session this intent is about.
     *
     * A session id is one this application already recorded, so it is read back rather than built:
     * an untitled document has no URI to derive an identity from, and a restored one must keep the
     * id its snapshot is filed under.
     */
    private suspend fun recordFor(
        intent: Intent,
        storage: Storage,
    ): SessionRecord? =
        when (val session = sessionIdOf(intent)) {
            null -> documentUri(intent)?.let { uri -> storage.opening(uri) }
            else -> reopened(storage.sessions, storage.snapshots, session)
        }

    /** A document from outside: its grant persisted (7.3), and its kind decided, as it is let in. */
    private suspend fun Storage.opening(uri: Uri): SessionRecord? {
        val kind = kindOf(uri) ?: return null

        return androidIdentity(this@DocumentActivity, uri, kind.id)?.let { sessions.opened(it) }
    }

    /**
     * The document this launch is about, wherever the sender chose to put it.
     *
     * `VIEW` and `EDIT` carry it as the intent's own data; `SEND` carries it as a stream extra.
     * 9.2 asks for all three, and a filter that matched `SEND` while the code only read `data`
     * would open an empty document from a share sheet -- with no error, because nothing failed.
     */
    private fun documentUri(intent: Intent): Uri? =
        when (intent.action) {
            Intent.ACTION_SEND -> sharedStream(intent)
            else -> intent.data
        }

    /** `EXTRA_STREAM`, through the compatibility accessor rather than the deprecated one. */
    @Suppress("DEPRECATION")
    private fun sharedStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    /**
     * What Recents calls this task.
     *
     * 7.2: "supply `ActivityManager.TaskDescription` with the document title so Recents is
     * legible." Without it every open document is the application's name, and a reader with three
     * chapters open has three identical cards.
     */
    private fun describeTask(displayName: String) {
        // The builder is API 33 and this application supports 24. The deprecated constructor says
        // the same thing on the devices in between, and saying nothing there would leave every
        // open document sharing one name in Recents on most phones in use.
        //
        // Two statements rather than one expression: lint reads a version check it can see at
        // statement level and not one buried inside an argument.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(
                ActivityManager.TaskDescription
                    .Builder()
                    .setLabel(displayName)
                    .build(),
            )
        } else {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(displayName))
        }
    }

    /**
     * What kind of document this is, by the three signals 9.1 and 9.2 give, in order of trust.
     *
     * The name first: it is what the reader chose and the only reliable signal Fountain has, since
     * 9.1 says it has no registered MIME type. Then the MIME type. Then the contents -- 9.2's
     * "sniff content on open", for the downloads and messaging apps that "frequently hand over
     * `application/octet-stream` regardless of the real type".
     */
    private fun kindOf(uri: Uri): DocumentKind? {
        // The name first, and decisively: it is what the reader chose, it is the only reliable
        // signal Fountain has, and a document they named `.md` is one they mean to edit whatever
        // its bytes look like.
        val named = displayNameOf(contentResolver, uri)?.let(DocumentKind::of)

        // Lazily, so a name that settles it does not also cost a read. Once read, read once: both
        // of the questions below ask the same bytes a different thing.
        val opening by lazy { opening(uri) }

        return when {
            named != null -> {
                named
            }

            // 9.2's filters accept `application/octet-stream`, so this is where a photo or an
            // archive pointed at Drafts is turned away -- before a session exists for it, and
            // before the reader is shown a screenful of replacement characters saying nothing.
            opening?.let { !DocumentKind.looksLikeText(it) } == true -> {
                null
            }

            else -> {
                contentResolver.getType(uri)?.let(DocumentKind::ofMimeType)
                    ?: opening?.let { DocumentKind.sniff(it.decodeToString()) }
                    ?: DocumentKind.Markdown
            }
        }
    }

    /** The opening of the document, which is as much as either question needs. */
    private fun opening(uri: Uri): ByteArray? =
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream -> stream.readNBytes(SNIFF_BYTES) }
        }.getOrNull()

    /**
     * What Recents calls a document opened from a project: its name, then the project and folder
     * (`projects/project-windows`, "Document windows stay associated with their project").
     */
    @Composable
    private fun titleOf(name: String): String {
        val project = shown?.getStringExtra(EXTRA_PROJECT_NAME) ?: return name
        val folder = shown?.getStringExtra(EXTRA_PROJECT_FOLDER)?.trim('/').orEmpty()
        val place = if (folder.isEmpty()) project else stringResource(Res.string.breadcrumb, project, folder)
        return stringResource(Res.string.document_in_project_title, name, place)
    }

    /** The ways to projects; and, for a document opened from one, the way back to it. */
    private fun projectActions(intent: Intent): ProjectActions {
        val project =
            intent.getStringExtra(EXTRA_PROJECT)?.let {
                ProjectLocation(DocumentRef(it), intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty())
            }
        return ProjectActions(
            openFolder = {
                startActivity(Intent(this, ProjectsActivity::class.java).putExtra(ProjectsActivity.EXTRA_CHOOSE, true))
            },
            recent = { startActivity(Intent(this, ProjectsActivity::class.java)) },
            showProject = project?.let { location -> { startActivity(projectIntent(this, location)) } },
        )
    }

    /**
     * A document opened from outside that is in a project this device has opened, and not open now:
     * the writer is offered that project too, and it is never opened unasked. Asked once per launch.
     */
    @Composable
    private fun OfferProject(open: SessionRecord) {
        val uri = open.uri?.takeIf { shown?.hasExtra(EXTRA_PROJECT) == false } ?: return
        LaunchedEffect(uri) {
            val project = enclosingProject(this@DocumentActivity, Uri.parse(uri)) ?: return@LaunchedEffect
            if (AndroidProjects
                    .list(
                        this@DocumentActivity,
                    ).open()
                    .any { it.token == project.token }
            ) {
                return@LaunchedEffect
            }
            val message = getString(Res.string.document_in_project, open.displayName, project.name)
            val yes = getString(Res.string.open_project)
            val no = getString(Res.string.cancel)
            AlertDialog
                .Builder(this@DocumentActivity)
                .setMessage(message)
                .setPositiveButton(
                    yes,
                ) { _, _ ->
                    startActivity(
                        projectIntent(this@DocumentActivity, ProjectLocation(DocumentRef(project.token), project.name)),
                    )
                }.setNegativeButton(no, null)
                .show()
        }
    }

    internal companion object {
        /** Enough for a title page and the first scene, without pulling a novel over the wire. */
        const val SNIFF_BYTES = 8 * 1024

        /**
         * A document opened from a project's binder: which project, by its folder's token, its name,
         * and the folder within it.
         */
        const val EXTRA_PROJECT = "com.appthere.drafts.PROJECT"
        const val EXTRA_PROJECT_NAME = "com.appthere.drafts.DOCUMENT_PROJECT_NAME"
        const val EXTRA_PROJECT_FOLDER = "com.appthere.drafts.PROJECT_FOLDER"
    }
}
