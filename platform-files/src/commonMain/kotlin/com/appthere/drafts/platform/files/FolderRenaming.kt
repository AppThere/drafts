package com.appthere.drafts.platform.files

/**
 * Renames a file or folder of the reader's own, in place: `projects.md` 3's `prefix` mode, where
 * "reordering renames files `01-`, `02-`; order is visible outside the app".
 *
 * Separate from [FolderListing], which only reads, so that what can change a name in the reader's
 * folders is one small thing to find. A rename never replaces: a name already taken is
 * [RenameOutcome.Taken], and nothing moves. Replacing would delete a file of the reader's, which
 * no reorder has any business doing.
 */
interface FolderRenaming {
    /** Renames [ref] to [name] in the folder it is in. */
    suspend fun rename(
        ref: DocumentRef,
        name: String,
    ): RenameOutcome
}

/** What a rename did. */
sealed interface RenameOutcome {
    /** Renamed; [ref] is where it is now. */
    data class Renamed(
        val ref: DocumentRef,
    ) : RenameOutcome

    /** Something is already called that. Nothing moved. */
    data object Taken : RenameOutcome

    /** It could not be renamed -- gone, not permitted, the disk refused. Nothing moved. */
    data class Failed(
        val detail: String,
    ) : RenameOutcome
}
