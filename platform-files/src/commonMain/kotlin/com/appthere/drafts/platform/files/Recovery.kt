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
    /**
     * Where the reader was, per 7.3's record, or null if nothing was recorded.
     *
     * Carried in both outcomes. Whether there is work to restore and where the reader had got to
     * are separate questions: a document read, scrolled and closed without an edit has nothing to
     * recover and still has a place to go back to.
     */
    val record: SessionRecord?

    /** No snapshot, or one that matches the file. Nothing to tell the reader about. */
    data class NothingToRestore(
        override val record: SessionRecord? = null,
    ) : Recovery

    /**
     * The snapshot holds work the file does not.
     *
     * [record] is null when `meta.json` could not be read. The text is still restored -- 8.3 says
     * "Never auto-discard a snapshot", and an unreadable caret position is not a reason to discard
     * the sentence it was in.
     */
    data class UnsavedWork(
        val text: String,
        override val record: SessionRecord?,
    ) : Recovery
}
