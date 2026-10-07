package com.appthere.drafts.platform.files

/**
 * What is in a folder of the reader's own: the one capability here that enumerates their
 * filesystem.
 *
 * Kept apart from [DocumentStore], whose `children` says "Nothing here enumerates the reader's own
 * filesystem" and lists only storage this application owns. A project (`projects.md` 1) is the
 * reader's folder, and the binder is that folder's tree, so a project cannot be opened without
 * looking inside it. It is a separate interface so that what may list the reader's folders stays
 * one thing, findable and small: it reads names and nothing else, and never writes.
 */
interface FolderListing {
    /**
     * What is directly inside [folder], in no particular order, or null if it could not be read --
     * gone, or not permitted. Null rather than empty, because an empty folder and one that could
     * not be read call for different words to the reader, and reconciliation (`projects.md` 9) must
     * not mistake the second for every file in it having been deleted.
     */
    suspend fun entriesOf(folder: DocumentRef): List<FolderEntry>?

    /**
     * Where a thing called [name] inside [folder] is, or would be: a name, not a check that it
     * exists. For the files a project keeps in its sidecar, which have to be written before they
     * can be listed.
     */
    fun childOf(
        folder: DocumentRef,
        name: String,
    ): DocumentRef
}

/** One thing in a folder: its name, how to reach it, and whether it is a folder in turn. */
data class FolderEntry(
    val name: String,
    val ref: DocumentRef,
    val isFolder: Boolean,
    val modifiedEpochMillis: Long,
)
