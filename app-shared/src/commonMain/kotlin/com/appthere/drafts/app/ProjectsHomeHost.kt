package com.appthere.drafts.app

/** What the projects screen asks of its platform: opening, finding and forgetting projects. */
interface ProjectsHomeHost {
    fun open(entry: ProjectEntry)

    /** The folder has moved or its access has gone: the writer chooses where it is now. */
    fun chooseAgain(entry: ProjectEntry)

    fun pin(
        entry: ProjectEntry,
        pinned: Boolean,
    )

    fun remove(entry: ProjectEntry)

    fun openFolder()

    fun newProject()

    /** Whether [entry]'s folder can still be reached. */
    fun isAvailable(entry: ProjectEntry): Boolean
}
