package com.appthere.drafts.app.android

/**
 * Which documents this process currently has on screen.
 *
 * `appthere-drafts.md` 7.4 turns on the difference: "Launching while the application is already
 * running opens a new untitled document in a new window", as against a launch that restores 7.3's
 * sessions. Android has no window list to count, and Recents is the wrong place to look -- an entry
 * can outlive the process it belonged to, so a launch after a force-stop would read as "already
 * running" and quietly stop restoring anything, which is the one case 7.3 exists for.
 *
 * A set in the process is exactly the right lifetime: it is empty when the process is new, which is
 * when a launch means "restore", and it is not when a document task is alive, which is when a
 * launch means "another one". Nothing persists it, deliberately.
 */
internal object OpenDocuments {
    private val showing = mutableSetOf<String>()

    /** Whether any document task in this process has a document on screen. */
    val any: Boolean
        @Synchronized get() = showing.isNotEmpty()

    @Synchronized
    fun opened(documentId: String) {
        showing += documentId
    }

    @Synchronized
    fun closed(documentId: String) {
        showing -= documentId
    }
}
