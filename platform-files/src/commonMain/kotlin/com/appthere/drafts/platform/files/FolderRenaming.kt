package com.appthere.drafts.platform.files

/**
 * Renames a file or folder of the reader's own, in place: `projects.md` 3's `prefix` mode, where
 * "reordering renames files `01-`, `02-`; order is visible outside the app".
 *
 * Separate from [FolderListing], which only reads, so that what can change a name in the reader's
 * folders is one small thing to find. A rename never replaces: a name already taken is
 * [FolderOutcome.Taken], and nothing moves. Replacing would delete a file of the reader's, which
 * no reorder has any business doing.
 *
 * A reorder needs nothing more than this; the binder's other changes are [FolderEditing]'s.
 */
interface FolderRenaming {
    /** Renames [ref] to [name] in the folder it is in. */
    suspend fun rename(
        ref: DocumentRef,
        name: String,
    ): FolderOutcome
}
