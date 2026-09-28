package com.appthere.drafts.platform.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The reader's own documents on Android, through the Storage Access Framework.
 *
 * A [DocumentRef]'s token is a `content://` URI. It is not a path and cannot be turned into one:
 * the document may live in another application's provider, on a network share, or in a cloud
 * account that materialises the bytes only when asked for them.
 *
 * **This store cannot write atomically, and says so.** SAF has no operation that replaces one
 * document with another in a single step -- `renameDocument` fails if the target name is taken, and
 * there is no rename-over. A write therefore truncates the document and refills it, so a process
 * killed in the middle leaves the reader's file short. That is a property of the API rather than of
 * this code, and nothing here can fix it.
 *
 * What protects the reader is the separation `appthere-drafts.md` 8 is built on: "Two distinct
 * guarantees, two distinct mechanisms." 8.2's digest check still holds in full -- a file changed on
 * disk is still refused, because that is a comparison and not a write. And 8.1's snapshot still
 * holds in full, because snapshots go to app-private storage through [PathDocumentStore], which is
 * a real filesystem. So the words survive a crash mid-save even when the file does not, and 8.3
 * restores them on the next launch. [SnapshotStore] refuses to be constructed over this store, so
 * that arrangement cannot be got wrong by accident.
 */
class SafDocumentStore(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DocumentStore {
    /** See the class comment: SAF offers no replace-in-one-step, so 8.1 cannot be honoured here. */
    override val writesAtomically: Boolean = false

    override suspend fun read(ref: DocumentRef): DocumentContents =
        withContext(io) {
            val bytes = bytesOf(ref) ?: throw FileNotFoundException("${ref.token} could not be opened")

            DocumentContents(
                text = bytes.decodeToString(),
                facts = factsOf(ref, bytes),
                writable = isWritable(ref),
            )
        }

    override suspend fun facts(ref: DocumentRef): FileFacts? =
        withContext(io) { bytesOf(ref)?.let { factsOf(ref, it) } }

    /**
     * 8.2 in full, which does not depend on atomicity.
     *
     * The check is a comparison and the refusal is the absence of a write, so both work exactly as
     * they do on desktop. Only the write that follows a *successful* check is weaker here.
     */
    override suspend fun writeIfUnchanged(
        ref: DocumentRef,
        text: String,
        expected: Digest,
    ): WriteOutcome =
        withContext(io) {
            val current =
                bytesOf(ref)
                    ?: return@withContext WriteOutcome.Unavailable(WriteOutcome.Reason.Missing, "${ref.token} is gone")

            val found = sha256(current)
            if (found != expected) {
                return@withContext WriteOutcome.Conflict(expected = expected, found = found)
            }
            put(ref, text)
        }

    /**
     * Writes, but not atomically. The name is the interface's; [writesAtomically] is the truth.
     *
     * Kept rather than made to fail, because the reader still has to be able to save. A store that
     * refused every write would protect the file by making the application useless.
     */
    override suspend fun writeAtomically(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome = withContext(io) { put(ref, text) }

    /**
     * The documents inside a tree the reader has granted.
     *
     * Only meaningful for a tree URI; a single granted document has no children and reports none.
     * Used by 7.3's session walk, which on Android walks app-private storage rather than this.
     */
    override suspend fun children(ref: DocumentRef): List<DocumentRef> =
        withContext(io) {
            runCatching {
                val tree = Uri.parse(ref.token)
                val childrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree,
                        DocumentsContract.getTreeDocumentId(tree),
                    )

                resolver
                    .query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                    ?.use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(
                                    DocumentRef(
                                        DocumentsContract
                                            .buildDocumentUriUsingTree(
                                                tree,
                                                cursor.getString(0),
                                            ).toString(),
                                    ),
                                )
                            }
                        }
                    }.orEmpty()
            }.getOrDefault(emptyList())
        }

    override suspend fun delete(ref: DocumentRef): Boolean =
        withContext(io) {
            runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(ref.token)) }.getOrDefault(false)
        }

    /** "wt" is truncate-then-write: the only mode SAF offers for replacing a document's contents. */
    private fun put(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome =
        try {
            val bytes = text.encodeToByteArray()
            resolver.openOutputStream(Uri.parse(ref.token), "wt")?.use { it.write(bytes) }
                ?: return WriteOutcome.Unavailable(WriteOutcome.Reason.Denied, "${ref.token} is not writable")

            WriteOutcome.Written(factsOf(ref, bytes))
        } catch (missing: FileNotFoundException) {
            WriteOutcome.Unavailable(WriteOutcome.Reason.Missing, missing.message ?: ref.token)
        } catch (failed: IOException) {
            WriteOutcome.Unavailable(WriteOutcome.Reason.Failed, failed.message ?: ref.token)
        } catch (denied: SecurityException) {
            // The grant lapsed between opening the document and saving it: the reader revoked it,
            // or the provider was uninstalled. 8.4 calls the result `orphaned`.
            WriteOutcome.Unavailable(WriteOutcome.Reason.Denied, denied.message ?: ref.token)
        }

    private fun bytesOf(ref: DocumentRef): ByteArray? =
        runCatching { resolver.openInputStream(Uri.parse(ref.token))?.use { it.readBytes() } }.getOrNull()

    private fun isWritable(ref: DocumentRef): Boolean =
        runCatching {
            val uri = Uri.parse(ref.token)
            resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission } ||
                DocumentsContract.isDocumentUri(null, uri)
        }.getOrDefault(false)

    /**
     * The facts 8.2 records. The digest is over bytes already in hand; SAF offers none of its own.
     *
     * The modified time comes from the provider when it has one and is zero when it does not --
     * some providers do not report it. Nothing depends on it: 8.2's comparison is the digest, and
     * the mtime is recorded because 7.3 asks for it.
     */
    private fun factsOf(
        ref: DocumentRef,
        bytes: ByteArray,
    ): FileFacts =
        FileFacts(
            digest = sha256(bytes),
            size = bytes.size.toLong(),
            modifiedEpochMillis = modifiedOf(ref),
        )

    private fun modifiedOf(ref: DocumentRef): Long =
        runCatching {
            val columns = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, OpenableColumns.SIZE)
            resolver.query(Uri.parse(ref.token), columns, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
}
