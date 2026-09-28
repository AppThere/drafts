package com.appthere.drafts.platform.files

/**
 * What a document's snapshot turned out to hold, per `appthere-drafts.md` 8.3.
 *
 * Two outcomes and no third. Either the snapshot says the same thing as the file -- the ordinary
 * case, because most sessions end with a save -- or it holds words that are not in the file, and
 * those words are the reader's.
 *
 * A missing or unreadable snapshot is [NothingToRestore] rather than an error. Most documents have
 * never been snapshotted, and the launch path walks every restored session: one unreadable
 * directory must not stop the others being examined.
 */
sealed interface Recovery {
    /** No snapshot, or one that matches the file. Nothing to tell the reader about. */
    data object NothingToRestore : Recovery

    /**
     * The snapshot holds work the file does not.
     *
     * [record] is null when `meta.json` could not be read. The text is still restored -- 8.3 says
     * "Never auto-discard a snapshot", and an unreadable caret position is not a reason to discard
     * the sentence it was in.
     */
    data class UnsavedWork(
        val text: String,
        val record: SessionRecord?,
    ) : Recovery
}
