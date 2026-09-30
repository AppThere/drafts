package com.appthere.drafts.app.android

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.appthere.drafts.app.DocumentOpening
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.app.Notice
import com.appthere.drafts.app.SettingsStore
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.rememberSessionDocument
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SafDocumentStore
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.androidIdentity
import com.appthere.drafts.platform.files.androidSessionRoot
import com.appthere.drafts.platform.files.androidSettingsRoot
import com.appthere.drafts.platform.files.displayNameOf
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList

/**
 * One document, in one task.
 *
 * `appthere-drafts.md` 7.2 on Android: "Launch each document with `FLAG_ACTIVITY_NEW_DOCUMENT or
 * FLAG_ACTIVITY_MULTIPLE_TASK`. Each document then appears as its own entry in Recents and can be
 * dragged into split-screen against another instance of the app -- which is what makes side-by-side
 * work on tablets, foldables, and ChromeOS."
 *
 * The per-document `taskAffinity` is set here rather than in the manifest, because a manifest value
 * is one string for every launch and the point is that each document gets its own. Recents shows
 * the document's name for the same reason: a column of identical entries is not a document switcher.
 */
class DocumentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shown = intent
        setContent { Document(shown?.let(::documentUri)) }
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

    /** The intent this task is about, which a later launch can replace. */
    private var shown: Intent? by mutableStateOf(null)

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

    @Composable
    private fun Document(uri: Uri?) {
        if (uri == null) {
            Notice(message = NO_DOCUMENT)
            return
        }

        val documents = remember { SafDocumentStore(contentResolver) }
        val files = remember { PathDocumentStore() }
        val snapshots = remember { SnapshotStore(files, androidSessionRoot(this)) }
        val sessions = remember { SessionList(snapshots) }
        val settings = remember { SettingsStore(files, androidSettingsRoot(this)) }

        // 9.2: "Call `takePersistableUriPermission` immediately on receiving a content URI, or
        // session restoration will fail silently on next launch." `androidIdentity` does it, and
        // this is the first moment it can be done.
        val record by produceState<SessionRecord?>(null, uri) {
            val identity = androidIdentity(this@DocumentActivity, uri, kindOf(uri).id)
            value = identity?.let { sessions.opened(it) }
        }

        val open = record
        if (open == null) {
            Notice(message = OPENING)
            return
        }

        // Recents shows one entry per document, named for the document. Set once the name is known.
        LaunchedEffect(open.displayName) { describeTask(open.displayName) }

        when (val opening = rememberSessionDocument(documents, snapshots, open)) {
            is DocumentOpening.Opened -> {
                DraftsApp(
                    document = opening.document,
                    initialSettings = ReaderSettings(),
                    keeper =
                        remember(
                            opening.document,
                        ) { SnapshotKeeper(opening.document, snapshots, open.identity()) },
                    settingsStore = settings,
                    kind = open.kind,
                )
            }

            DocumentOpening.Opening -> {
                Notice(message = OPENING)
            }

            is DocumentOpening.Failed -> {
                Notice(message = COULD_NOT_OPEN, name = open.displayName)
            }
        }
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
    private fun kindOf(uri: Uri): DocumentKind {
        val named = displayNameOf(contentResolver, uri)?.let(DocumentKind::of)
        val typed = contentResolver.getType(uri)?.let(DocumentKind::ofMimeType)

        return named ?: typed ?: sniffed(uri) ?: DocumentKind.Markdown
    }

    /** Reads only as much as [DocumentKind.sniff] looks at. */
    private fun sniffed(uri: Uri): DocumentKind? =
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                DocumentKind.sniff(stream.readNBytes(SNIFF_BYTES).decodeToString())
            }
        }.getOrNull()

    private companion object {
        /** Enough for a title page and the first scene, without pulling a novel over the wire. */
        const val SNIFF_BYTES = 8 * 1024

        const val NO_DOCUMENT = "No document was given to open."
        const val OPENING = "Opening…"
        const val COULD_NOT_OPEN = "Could not open"
    }
}

/** The 7.3 identity a record describes, for the keeper that writes its snapshots. */
private fun SessionRecord.identity() =
    com.appthere.drafts.platform.files.SessionIdentity(
        documentId = documentId,
        uri = uri,
        displayName = displayName,
        kind = kind,
        accessToken = accessToken,
    )
