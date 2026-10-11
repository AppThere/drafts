package com.appthere.drafts.app.android

import android.app.ActivityManager
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.appthere.drafts.app.DocumentOpening
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.app.HostActions
import com.appthere.drafts.app.KindChange
import com.appthere.drafts.app.Notice
import com.appthere.drafts.app.OpenDocument
import com.appthere.drafts.app.ProjectActions
import com.appthere.drafts.app.SaveAs
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.kindOf
import com.appthere.drafts.app.noticeFor
import com.appthere.drafts.app.rememberSessionDocument
import com.appthere.drafts.app.suggestedSaveName
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.document_gone
import com.appthere.drafts.i18n.resources.my_version
import com.appthere.drafts.i18n.resources.no_document
import com.appthere.drafts.i18n.resources.not_a_text_document
import com.appthere.drafts.i18n.resources.opening
import com.appthere.drafts.i18n.resources.untitled
import com.appthere.drafts.platform.files.SafDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.androidIdentity
import com.appthere.drafts.platform.files.displayNameOf
import com.appthere.drafts.platform.intents.DocumentKind
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * One open session: its settings, its document, and the two things that can move it. Hosted by a
 * document's own task, or by a project's editor pane (`projects/project-windows`).
 */
@Composable
internal fun ComponentActivity.DocumentSession(
    open: SessionRecord,
    storage: Storage,
    documents: SafDocumentStore,
    onMove: (SessionRecord) -> Unit,
    onClosing: ((suspend () -> Unit)?) -> Unit,
    onTitle: (String) -> Unit = {},
    projects: ProjectActions? = null,
) {
    // Recents shows one entry per document, named for the document.
    // Read through `rememberUpdatedState`: the effects below outlive the composition that starts them.
    val title by rememberUpdatedState(onTitle)
    val closing by rememberUpdatedState(onClosing)
    LaunchedEffect(open.displayName) { title(open.displayName) }

    // 5.5's settings for this document's type, read before the document is drawn so the reader
    // never sees the defaults flash up and be replaced by their own typography.
    val saved by produceState<ReaderSettings?>(null, storage.settings, open.kind) {
        value = storage.settings.settingsFor(open.kind) ?: ReaderSettings()
    }

    when (val opening = rememberSessionDocument(documents, storage.snapshots, open, storage.settings)) {
        is DocumentOpening.Opened -> {
            val keeper =
                remember(opening.document) {
                    SnapshotKeeper(opening.document, storage.snapshots, open.identity())
                }
            val saving = remember(storage) { SaveAs(storage.sessions, storage.settings) { onMove(it) } }
            val kinds = remember(storage) { KindChange(storage.sessions, storage.settings) { onMove(it) } }
            val choose = rememberSaveLocation()
            val scope = rememberCoroutineScope()
            val openFile =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
                    chosen?.let(::openInItsOwnWindow)
                }

            Closable(open, storage, keeper, opening.document, closing)

            saved?.let { initial ->
                DraftsApp(
                    document = opening.document,
                    initialSettings = initial,
                    keeper = keeper,
                    settingsStore = storage.settings,
                    kind = open.kind,
                    saveAs = {
                        // 7.4 while untitled, 8.2's "Save a copy..." while conflicted, the
                        // file's own name otherwise -- all decided in `:app-shared`, because
                        // none of it is about Android.
                        val suggested =
                            opening.document.suggestedSaveName(
                                open,
                                untitled = getString(Res.string.untitled),
                                myVersion = getString(Res.string.my_version),
                            )
                        val where = SaveLocation(mimeFor(open.kind), suggested)

                        choose(where)?.let { chosen ->
                            saving.to(destinationFor(chosen, open), opening.document, open, keeper)
                        }
                    },
                    // 7.4's kind, while there is no file whose extension already says.
                    onKindChange =
                        if (open.uri == null) {
                            { chosen -> scope.launch { kinds.to(chosen, open, keeper, opening.document.editor) } }
                        } else {
                            null
                        },
                    // 7.1: another document is another task. New is the request tapping the
                    // icon again makes (7.4); Open hands its choice over as Files would.
                    host =
                        HostActions(
                            newDocument = { startActivity(Intent(this, LauncherActivity::class.java)) },
                            openDocument = { openFile.launch(openable) },
                            projects = projects,
                        ),
                )
            }
        }

        DocumentOpening.Opening -> {
            Notice(message = stringResource(Res.string.opening))
        }

        is DocumentOpening.Failed -> {
            Notice(message = noticeFor(opening.reason), name = open.displayName)
        }
    }
}

/**
 * The ways this document is let go of: its closing handed over -- 8.1's closing snapshot happens
 * after the window has gone, and the composition is the only thing that knows what to snapshot --
 * and its file known to the projects while it is on screen, so that putting the file in the trash
 * can ask first if its words are not saved (`projects/trash`).
 */
@Composable
private fun Closable(
    open: SessionRecord,
    storage: Storage,
    keeper: SnapshotKeeper,
    document: OpenDocument,
    closing: ((suspend () -> Unit)?) -> Unit,
) {
    val handOver by rememberUpdatedState(closing)
    DisposableEffect(keeper, open.documentId) {
        handOver { closeDocument(storage.sessions, keeper, open.documentId) }
        onDispose { handOver(null) }
    }
    open.uri?.let { file ->
        DisposableEffect(document, file) {
            AndroidProjects.openDocuments[file] = document
            onDispose { AndroidProjects.openDocuments.remove(file, document) }
        }
    }
}

/**
 * A document chosen with Open, in a task of its own.
 *
 * Handed to a new `DocumentActivity` exactly as the Files app hands one over (9.2), so it is let
 * in the same way: its grant persisted, its kind decided, and a file this application cannot
 * read answered with a notice in that window rather than nothing at all. `ACTION_OPEN_DOCUMENT`'s
 * grant is persistable and belongs to this application, so the new task can keep it.
 */
internal fun ComponentActivity.openInItsOwnWindow(uri: Uri) {
    startActivity(
        Intent(Intent.ACTION_VIEW, uri, this, DocumentActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ),
    )
}

/**
 * Where 7.4's first save has landed, in the terms the session list keeps.
 *
 * The name is the picker's, not the one that was suggested: the reader can type anything, and
 * the Storage Access Framework may add an extension of its own. The kind follows that name
 * (9.1), so saving an untitled document as `.fountain` makes it a screenplay.
 *
 * `androidIdentity` takes the persistable grant on the way past, which is what makes the
 * document restorable on the next launch (7.3). If it cannot -- a provider that grants nothing
 * persistable -- the save still happens and only the restore is lost, so the identity is built
 * without a token rather than the save being refused.
 */
private fun ComponentActivity.destinationFor(
    uri: Uri,
    record: SessionRecord,
): SessionIdentity {
    val name = displayNameOf(contentResolver, uri) ?: uri.lastPathSegment ?: record.displayName
    val kind = DocumentKind.of(name)?.id ?: record.kind

    return androidIdentity(this, uri, kind)
        ?: SessionIdentity(
            documentId = record.documentId,
            uri = uri.toString(),
            displayName = name,
            kind = kind,
        )
}

/**
 * The type `ACTION_CREATE_DOCUMENT` is asked to make.
 *
 * Fountain has none -- 9.1: "MIME | `text/markdown` (RFC 7763) | none registered" -- and the picker
 * requires one, so a screenplay is created as plain text. Its extension is what says what it is,
 * which is the same thing 9.2 already relies on when one arrives from elsewhere.
 */
private fun mimeFor(kind: String): String = kindOf(kind).mimeTypes.firstOrNull() ?: "text/plain"

/**
 * What Open's picker offers. Text, and the type Fountain usually arrives as -- it has none of its
 * own, so providers call it `application/octet-stream` -- matching the manifest's own filters.
 */
private val openable = arrayOf("text/*", "application/octet-stream")
