package com.appthere.drafts.platform.files

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A documents provider over a directory, for testing the Storage Access Framework code without the
 * system picker (`projects-core` tasks 2.3, 2.4, 3.2).
 *
 * It lives in the test APK, so the test reaches it as its own provider and needs no grant. It
 * behaves as Android's own storage provider does where that matters -- ids are paths and change
 * when a document is renamed or moved, a name already taken on create or rename is made unique,
 * a move onto a taken name throws, and changes are notified on the parent's children URI -- and it
 * can be told to misbehave the ways other providers do: no move, a `:` it cannot store, no modified
 * time.
 *
 * What it cannot show is how a real third-party provider behaves; that is the manual pass's.
 */
class TestDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: ROOT_COLUMNS).apply {
            newRow()
                .add(Root.COLUMN_ROOT_ID, ROOT)
                .add(Root.COLUMN_DOCUMENT_ID, ROOT)
                .add(Root.COLUMN_TITLE, "Test")
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
        }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply { row(this, fileOf(documentId)) }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            // As Android's storage provider does: a folder that is not there is an error, not empty.
            val parent = fileOf(parentDocumentId)
            if (!parent.isDirectory) throw FileNotFoundException("Missing file for $parentDocumentId")
            parent.listFiles().orEmpty().forEach { row(this, it) }
            setNotificationUri(resolver(), DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId))
        }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileOf(documentId)
        if (mode.contains('w')) notifyParentOf(file)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val file = unique(fileOf(parentDocumentId), displayName)
        val made = if (mimeType == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()
        check(made) { "Could not create $file" }
        notifyParentOf(file)
        return idOf(file)
    }

    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        val before = fileOf(documentId)
        // As a provider over FAT does with a character it cannot store.
        val wanted = if (altersNames) displayName.replace(':', '_') else displayName
        val after = unique(before.parentFile!!, wanted)
        check(before.renameTo(after)) { "Could not rename $before" }
        notifyParentOf(after)
        return idOf(after)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileOf(documentId)
        check(file.deleteRecursively()) { "Could not delete $file" }
        notifyParentOf(file)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        if (!supportsMove) throw UnsupportedOperationException("Move not supported")
        val before = fileOf(sourceDocumentId)
        val after = File(fileOf(targetParentDocumentId), before.name)
        check(!after.exists()) { "Already exists $after" }
        check(before.renameTo(after)) { "Could not move $before" }
        notifyParentOf(before)
        notifyParentOf(after)
        return idOf(after)
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean =
        parentDocumentId == ROOT || documentId == parentDocumentId || documentId.startsWith("$parentDocumentId/")

    private fun row(
        cursor: MatrixCursor,
        file: File,
    ) {
        if (!file.exists()) throw FileNotFoundException("$file")
        var flags = Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        if (supportsMove) flags = flags or Document.FLAG_SUPPORTS_MOVE
        if (file.isDirectory) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE

        cursor
            .newRow()
            .add(Document.COLUMN_DOCUMENT_ID, idOf(file))
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(
                Document.COLUMN_MIME_TYPE,
                if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream",
            ).add(Document.COLUMN_LAST_MODIFIED, if (reportsModified) file.lastModified() else null)
            .add(Document.COLUMN_FLAGS, flags)
            .add(Document.COLUMN_SIZE, file.length())
    }

    /** As Android's storage provider does: `name (1).ext` when `name.ext` is taken. */
    private fun unique(
        parent: File,
        name: String,
    ): File {
        val first = File(parent, name)
        if (!first.exists()) return first
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return generateSequence(1) { it + 1 }.map { File(parent, "$base ($it)$extension") }.first { !it.exists() }
    }

    private fun notifyParentOf(file: File) {
        val parent = file.parentFile ?: return
        resolver().notifyChange(DocumentsContract.buildChildDocumentsUri(AUTHORITY, idOf(parent)), null)
    }

    private fun resolver() = requireNotNull(context).contentResolver

    private fun fileOf(id: String): File =
        if (id ==
            ROOT
        ) {
            top(requireNotNull(context))
        } else {
            File(top(requireNotNull(context)), id)
        }

    private fun idOf(file: File): String {
        val top = top(requireNotNull(context))
        return if (file == top) ROOT else file.relativeTo(top).path
    }

    companion object {
        const val AUTHORITY = "com.appthere.drafts.platform.files.test.documents"
        const val ROOT = "root"

        @Volatile var supportsMove = true

        @Volatile var altersNames = false

        @Volatile var reportsModified = true

        /** The granted folder, as the system picker would have handed it over. */
        val tree: Uri get() = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT)

        /** The directory behind the provider, emptied, with every switch back to normal. */
        fun reset(context: Context): File {
            supportsMove = true
            altersNames = false
            reportsModified = true
            return top(context).apply {
                deleteRecursively()
                mkdirs()
            }
        }

        private fun top(context: Context): File = File(context.filesDir, "saf-test")

        private val ROOT_COLUMNS =
            arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)
        private val DOCUMENT_COLUMNS =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_FLAGS,
                Document.COLUMN_SIZE,
            )
    }
}
