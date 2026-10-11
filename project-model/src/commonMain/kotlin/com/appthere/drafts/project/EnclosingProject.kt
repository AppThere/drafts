package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEntry
import com.appthere.drafts.platform.files.FolderListing

/**
 * Whether a folder is inside a project already (`projects/folder-as-project`, "A folder inside a
 * project says so"; `projects.md` 13.2, "the nearest `.drafts/` wins").
 *
 * Asked of every folder above, nearest first, up to the top of the filesystem. Where the platform
 * cannot say what is above -- Android's storage framework -- the answer is [Enclosing.CannotTell],
 * and the app makes no claim either way.
 */
class EnclosingProject(
    private val listing: FolderListing,
) {
    suspend fun of(folder: DocumentRef): Enclosing {
        var above = listing.parentOf(folder)
        var found: Enclosing = if (above == null) Enclosing.CannotTell else Enclosing.None
        while (above != null && found == Enclosing.None) {
            val project = listing.entriesOf(above.ref).orEmpty().any { it.isFolder && it.name == SIDECAR }
            if (project) found = Enclosing.Inside(above) else above = listing.parentOf(above.ref)
        }
        return found
    }

    private companion object {
        const val SIDECAR = ".drafts"
    }
}

/** What [EnclosingProject] found. */
sealed interface Enclosing {
    /** Nothing above is a project. */
    data object None : Enclosing

    /** [project] is: the folder is part of it. */
    data class Inside(
        val project: FolderEntry,
    ) : Enclosing

    /** The platform cannot see above the folder. */
    data object CannotTell : Enclosing
}
