package com.appthere.drafts.platform.files

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [FolderListing] over a folder the reader granted through the Storage Access Framework
 * (`projects-core` design D4).
 *
 * One query per folder, for exactly the four columns a listing needs, against the children URI
 * built from the tree. `DocumentFile.listFiles()` is what `projects.md` 9 calls "the performance
 * problem": it fetches each child's details one query at a time.
 *
 * A folder's [DocumentRef] is either the granted tree itself or a document under it; every entry
 * comes back as a document under the same tree, so the grant covers it. A provider that reports no
 * modification time gives 0, which reconciliation reads as "unknown, look again".
 */
class SafFolderListing(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FolderListing {
    override suspend fun entriesOf(folder: DocumentRef): List<FolderEntry>? =
        withContext(io) {
            runCatching {
                val uri = Uri.parse(folder.token)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentIdOf(context, uri))
                context.contentResolver.query(children, COLUMNS, null, null, null)?.use { cursor ->
                    generateSequence { cursor.takeIf { it.moveToNext() }?.let { entryAt(it, uri) } }.toList()
                }
            }.getOrNull()
        }

    /** The row [cursor] is on, as an entry reached through the same tree as its folder. */
    private fun entryAt(
        cursor: Cursor,
        tree: Uri,
    ) = FolderEntry(
        name = cursor.getString(NAME),
        ref = DocumentRef(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(ID)).toString()),
        isFolder = cursor.getString(MIME) == Document.MIME_TYPE_DIR,
        modifiedEpochMillis = if (cursor.isNull(MODIFIED)) 0L else cursor.getLong(MODIFIED),
    )

    override suspend fun modifiedOf(folder: DocumentRef): Long =
        withContext(io) {
            runCatching {
                val uri = Uri.parse(folder.token)
                val document = DocumentsContract.buildDocumentUriUsingTree(uri, documentIdOf(context, uri))
                context.contentResolver.query(document, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)?.use {
                    if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L
                }
            }.getOrNull() ?: 0L
        }

    /** Never known: a tree grant reaches down from the folder the reader chose, and not above it. */
    override suspend fun parentOf(folder: DocumentRef): FolderEntry? = null

    private companion object {
        val COLUMNS =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED,
            )
        const val ID = 0
        const val NAME = 1
        const val MIME = 2
        const val MODIFIED = 3
    }
}

/** The document a SAF reference names: a document under a tree, or the tree's own top. */
internal fun documentIdOf(
    context: Context,
    uri: Uri,
): String =
    if (DocumentsContract.isDocumentUri(context, uri)) {
        DocumentsContract.getDocumentId(uri)
    } else {
        DocumentsContract.getTreeDocumentId(uri)
    }
