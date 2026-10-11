package com.appthere.drafts.platform.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [FolderEditing] over a folder the reader granted through the Storage Access Framework
 * (`projects-core` design D5).
 *
 * Providers do not keep [FolderEditing]'s promise on their own. Android's own storage provider,
 * asked to create or rename onto a name that is taken, quietly makes the name unique --
 * `marla (1).md` -- and some providers change names they cannot store. So:
 *
 * - A name is checked against the folder's listing first, where the folder is known, and a taken
 *   one is [FolderOutcome.Taken] before anything is asked of the provider.
 * - Whatever the provider does, the name it actually used is read back. A different one is undone
 *   and [FolderOutcome.Failed]: a file under a name the reader did not choose is not what they
 *   asked for, and a reorder that went on with it would number the folder wrongly.
 * - New files are created as `application/octet-stream`, so that no provider appends an extension
 *   it thinks the type deserves.
 *
 * A move uses the provider's own where it offers one. Where it does not, the item is copied, the
 * copy is read back and compared byte for byte by digest, and only then is the original deleted. A
 * copy that fails is removed and the original is untouched; a delete that fails leaves two copies,
 * and says so. Neither loses a word.
 */
class SafFolderEditing(
    private val context: Context,
    private val listing: FolderListing = SafFolderListing(context),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FolderEditing {
    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun rename(
        ref: DocumentRef,
        name: String,
    ): FolderOutcome =
        changing(name) {
            val uri = Uri.parse(ref.token)
            val original = nameOf(uri) ?: return@changing FolderOutcome.Failed("${ref.token} is gone")
            val renamed =
                DocumentsContract.renameDocument(resolver, uri, name)
                    ?: return@changing FolderOutcome.Failed("The provider would not rename ${ref.token}")

            val actual = nameOf(renamed)
            if (actual == name) return@changing FolderOutcome.Done(DocumentRef(renamed.toString()))

            val restored = DocumentsContract.renameDocument(resolver, renamed, original)?.let(::nameOf)
            val now = if (restored == original) "it is $original again" else "it is now called ${restored ?: actual}"
            FolderOutcome.Failed("The provider named it $actual rather than $name; $now")
        }

    override suspend fun move(
        ref: DocumentRef,
        from: DocumentRef,
        to: DocumentRef,
    ): FolderOutcome =
        attempt {
            val uri = Uri.parse(ref.token)
            val name = nameOf(uri) ?: return@attempt FolderOutcome.Failed("${ref.token} is gone")
            if (isTaken(to, name)) return@attempt FolderOutcome.Taken

            if (supportsMove(uri)) {
                moved(uri, name, documentUriOf(Uri.parse(from.token)), documentUriOf(Uri.parse(to.token)))
            } else {
                copiedThenDeleted(uri, name, documentUriOf(Uri.parse(to.token)))
            }
        }

    override suspend fun createFile(
        folder: DocumentRef,
        name: String,
    ): FolderOutcome = created(folder, name, OCTET_STREAM)

    override suspend fun createFolder(
        folder: DocumentRef,
        name: String,
    ): FolderOutcome = created(folder, name, Document.MIME_TYPE_DIR)

    override suspend fun remove(ref: DocumentRef): FolderOutcome =
        attempt {
            if (DocumentsContract.deleteDocument(resolver, Uri.parse(ref.token))) {
                FolderOutcome.Done(ref)
            } else {
                FolderOutcome.Failed("The provider would not remove ${ref.token}")
            }
        }

    private suspend fun created(
        folder: DocumentRef,
        name: String,
        mime: String,
    ): FolderOutcome =
        changing(name) {
            if (isTaken(folder, name)) return@changing FolderOutcome.Taken

            val made =
                createIn(documentUriOf(Uri.parse(folder.token)), mime, name)
                    ?: return@changing FolderOutcome.Failed("The provider would not create $name")
            FolderOutcome.Done(DocumentRef(made.toString()))
        }

    /** Moved by the provider, and put back if the provider changed its name on the way. */
    private fun moved(
        uri: Uri,
        name: String,
        from: Uri,
        to: Uri,
    ): FolderOutcome {
        val moved = DocumentsContract.moveDocument(resolver, uri, from, to)
        val actual = moved?.let(::nameOf)

        return when {
            moved == null -> {
                FolderOutcome.Failed("The provider would not move $name")
            }

            actual == name -> {
                FolderOutcome.Done(DocumentRef(moved.toString()))
            }

            else -> {
                DocumentsContract.moveDocument(resolver, moved, to, from)
                FolderOutcome.Failed("The provider named it $actual rather than $name")
            }
        }
    }

    private fun copiedThenDeleted(
        uri: Uri,
        name: String,
        to: Uri,
    ): FolderOutcome {
        val copy = copyInto(uri, name, isFolder(uri), to) ?: return FolderOutcome.Failed("$name could not be copied")

        return if (DocumentsContract.deleteDocument(resolver, uri)) {
            FolderOutcome.Done(DocumentRef(copy.toString()))
        } else {
            FolderOutcome.Failed("$name was copied, but the original could not be removed; both are there")
        }
    }

    /** A verified copy of [source] in [parent], or null with nothing left behind. */
    private fun copyInto(
        source: Uri,
        name: String,
        folder: Boolean,
        parent: Uri,
    ): Uri? {
        val copy = createIn(parent, if (folder) Document.MIME_TYPE_DIR else OCTET_STREAM, name) ?: return null
        val copied =
            runCatching { if (folder) copiedChildren(source, copy) else copiedBytes(source, copy) }.getOrDefault(false)

        if (!copied) runCatching { DocumentsContract.deleteDocument(resolver, copy) }
        return copy.takeIf { copied }
    }

    private fun copiedBytes(
        source: Uri,
        copy: Uri,
    ): Boolean {
        val bytes = resolver.openInputStream(source)?.use { it.readBytes() }
        val written = bytes != null && resolver.openOutputStream(copy, "wt")?.use { it.write(bytes) } != null
        val back = if (written) resolver.openInputStream(copy)?.use { it.readBytes() } else null
        return bytes != null && back != null && sha256(back) == sha256(bytes)
    }

    private fun copiedChildren(
        source: Uri,
        copy: Uri,
    ): Boolean =
        childrenOf(source).all { (child, childName, childFolder) ->
            copyInto(child, childName, childFolder, copy) !=
                null
        }

    private fun childrenOf(folder: Uri): List<Triple<Uri, String, Boolean>> {
        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                folder,
                DocumentsContract.getDocumentId(folder),
            )
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)

        return resolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val child = DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(0))
                    add(Triple(child, cursor.getString(1), cursor.getString(2) == Document.MIME_TYPE_DIR))
                }
            }
        } ?: error("$folder could not be listed")
    }

    /** Created under exactly [name], or not at all. */
    private fun createIn(
        parent: Uri,
        mime: String,
        name: String,
    ): Uri? {
        val made = DocumentsContract.createDocument(resolver, parent, mime, name)
        val exact = made?.takeIf { nameOf(it) == name }
        if (made != null && exact == null) DocumentsContract.deleteDocument(resolver, made)
        return exact
    }

    private suspend fun isTaken(
        folder: DocumentRef,
        name: String,
    ): Boolean = listing.entriesOf(folder)?.any { it.name == name } ?: false

    private fun nameOf(uri: Uri): String? = stringOf(uri, Document.COLUMN_DISPLAY_NAME)

    private fun isFolder(uri: Uri): Boolean = stringOf(uri, Document.COLUMN_MIME_TYPE) == Document.MIME_TYPE_DIR

    private fun supportsMove(uri: Uri): Boolean =
        resolver.query(uri, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) and Document.FLAG_SUPPORTS_MOVE != 0
        } ?: false

    private fun stringOf(
        uri: Uri,
        column: String,
    ): String? =
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** A folder as a document the provider can be asked about: the tree's top is not one until built. */
    private fun documentUriOf(uri: Uri): Uri =
        if (DocumentsContract.isDocumentUri(context, uri)) {
            uri
        } else {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        }

    private suspend fun changing(
        name: String,
        change: suspend () -> FolderOutcome,
    ): FolderOutcome = if (isOneName(name)) attempt(change) else FolderOutcome.Failed("Not a single name: $name")

    private suspend fun attempt(change: suspend () -> FolderOutcome): FolderOutcome =
        withContext(io) {
            // What providers throw: gone, not permitted, already there, not supported. Whichever it
            // is, the reason goes back to the caller as a failure rather than out as a crash.
            try {
                change()
            } catch (failed: IOException) {
                failure(failed)
            } catch (failed: SecurityException) {
                failure(failed)
            } catch (failed: IllegalStateException) {
                failure(failed)
            } catch (failed: IllegalArgumentException) {
                failure(failed)
            } catch (failed: UnsupportedOperationException) {
                failure(failed)
            }
        }

    private fun failure(failed: Throwable): FolderOutcome =
        FolderOutcome.Failed(failed.message ?: failed::class.simpleName.orEmpty())

    private companion object {
        const val OCTET_STREAM = "application/octet-stream"
    }
}
