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
 *
 * A child is found by listing its folder, never by building its reference from a name: through
 * Android's storage framework a document's id is its provider's to choose, and a name says nothing
 * about it. What does not exist yet is made through [FolderEditing].
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
     * When [folder]'s own entries last changed, or 0 if the platform does not say or it cannot be
     * read. One question about one folder, much cheaper than listing it: a walk asks it of every
     * folder and lists only those whose answer moved (`projects-core` design D4).
     */
    suspend fun modifiedOf(folder: DocumentRef): Long

    /**
     * The folder [folder] is in, or null if it has none or the platform cannot say. Android's
     * storage framework cannot: a granted folder sees down, never up (`projects/folder-as-project`,
     * "where the platform does not let the app see the folder's enclosing folders").
     */
    suspend fun parentOf(folder: DocumentRef): FolderEntry?
}

/** One thing in a folder: its name, how to reach it, and whether it is a folder in turn. */
data class FolderEntry(
    val name: String,
    val ref: DocumentRef,
    val isFolder: Boolean,
    val modifiedEpochMillis: Long,
)
