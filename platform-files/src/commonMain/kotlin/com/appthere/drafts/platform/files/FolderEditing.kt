package com.appthere.drafts.platform.files

/**
 * Every change the binder makes to the reader's folders (`projects-core` design D5): a rename, a
 * move into another folder, a new file or folder, and the one removal there is, emptying the trash.
 *
 * The rule [FolderRenaming] keeps holds for all of it: **nothing is ever replaced**. A name already
 * taken is [FolderOutcome.Taken] and nothing has changed. Whatever fails is [FolderOutcome.Failed]
 * and, as far as the platform allows, has changed nothing either.
 */
interface FolderEditing : FolderRenaming {
    /**
     * Moves [ref] out of [from], the folder it is in, into [to], keeping its name. [from] is asked
     * for because Android's storage framework cannot move a document without being told where it is.
     */
    suspend fun move(
        ref: DocumentRef,
        from: DocumentRef,
        to: DocumentRef,
    ): FolderOutcome

    /** An empty file called [name] in [folder]; what goes in it is [DocumentStore]'s to write. */
    suspend fun createFile(
        folder: DocumentRef,
        name: String,
    ): FolderOutcome

    /** A folder called [name] in [folder]. */
    suspend fun createFolder(
        folder: DocumentRef,
        name: String,
    ): FolderOutcome

    /**
     * Removes [ref] for good, with everything in it if it is a folder. [FolderOutcome.Done] carries
     * the reference that no longer exists.
     *
     * For emptying the trash, which the reader asked for and confirmed (`projects/trash`). Nothing
     * else in the app deletes a file of the reader's: a binder delete is a [move] into the trash.
     */
    suspend fun remove(ref: DocumentRef): FolderOutcome
}

/** What a change to a folder did. */
sealed interface FolderOutcome {
    /** Done; [ref] is where the thing is now. */
    data class Done(
        val ref: DocumentRef,
    ) : FolderOutcome

    /** Something is already called that. Nothing changed. */
    data object Taken : FolderOutcome

    /** It could not be done -- gone, not permitted, the disk refused. */
    data class Failed(
        val detail: String,
    ) : FolderOutcome
}
