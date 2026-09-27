package com.appthere.drafts.platform.files

/**
 * The five states of `appthere-drafts.md` 8.4.
 *
 * "A document session is in exactly one of: `clean`, `dirty`, `conflicted`, `orphaned` (file
 * deleted or permission lost), or `readOnly`."
 *
 * Exactly one, which is the part worth taking seriously: the underlying facts are independent --
 * a document can have unsaved edits *and* be read-only *and* have changed on disk -- so something
 * has to decide which one the reader is shown. That decision lives in exactly one place, in
 * [DocumentSessionState.state], rather than being re-derived at each place that displays it.
 */
enum class DocumentState {
    /** Matches the file on disk. Nothing to save. */
    Clean,

    /** Has edits that are not in the file yet. The snapshot has them; the user's file does not. */
    Dirty,

    /** The file changed underneath us. 8.2 has refused a write, and asked the reader what to do. */
    Conflicted,

    /** The file is gone, or permission to it has been lost. */
    Orphaned,

    /** Can be read but not written. */
    ReadOnly,
}

/**
 * What is known about one open document, and which of 8.4's states that adds up to.
 *
 * The four properties are the facts; [state] is the single answer 8.4 asks for. Storing the answer
 * instead would mean every transition had to re-apply the precedence, and the one that forgot
 * would be the one that told a reader their document was clean while it held unsaved work.
 *
 * The precedence runs from least recoverable to most. A reader whose file has been deleted needs
 * to know that before they need to know they have unsaved edits -- and unsaved edits are exactly
 * what makes the deletion worth telling them about.
 *
 * 8.4 also says how this is shown: "Surface the state in the window chrome -- quietly, as a dot or
 * a short label, not a dialog. Dialogs only on attempted write." So this type carries no message
 * and no prompt. It is a state; the dialog belongs to the write that was refused.
 */
data class DocumentSessionState(
    val ref: DocumentRef,
    val base: FileFacts,
    val hasUnsavedEdits: Boolean = false,
    val writable: Boolean = true,
    val changedOnDiskTo: Digest? = null,
    val unreachable: Boolean = false,
) {
    val state: DocumentState
        get() =
            when {
                unreachable -> DocumentState.Orphaned
                changedOnDiskTo != null -> DocumentState.Conflicted
                !writable -> DocumentState.ReadOnly
                hasUnsavedEdits -> DocumentState.Dirty
                else -> DocumentState.Clean
            }

    /**
     * The reader has changed the document.
     *
     * A read-only document can still be edited -- the reader may be about to save a copy -- and
     * the edits are recorded here even though the state stays `readOnly`. Nothing is lost by that:
     * 8.1's snapshot runs regardless of state, because "autosave never touches the user's file"
     * and so has nothing to be blocked by.
     */
    fun edited(): DocumentSessionState = if (hasUnsavedEdits) this else copy(hasUnsavedEdits = true)

    /**
     * The outcome of an attempted write.
     *
     * A successful write is where `baseDigest` moves on, as 8.2 requires: "Explicit save uses the
     * same atomic temp-and-rename, then updates `baseDigest` to the newly written content." A
     * session that kept the old digest would refuse its own next save.
     */
    fun wrote(outcome: WriteOutcome): DocumentSessionState =
        when (outcome) {
            is WriteOutcome.Written -> {
                copy(base = outcome.facts, hasUnsavedEdits = false, changedOnDiskTo = null)
            }

            is WriteOutcome.Conflict -> {
                copy(changedOnDiskTo = outcome.found)
            }

            is WriteOutcome.Unavailable -> {
                when (outcome.reason) {
                    WriteOutcome.Reason.Missing, WriteOutcome.Reason.Denied -> copy(unreachable = true)

                    // The document is still on disk and still ours; the disk was just full, or the
                    // share dropped. The edits remain unsaved, which `dirty` already says. What the
                    // reader needs is the dialog 8.4 permits "on attempted write", not a new state.
                    WriteOutcome.Reason.Failed -> this
                }
            }
        }

    /**
     * The document has been re-read from disk, and what is in memory now matches it.
     *
     * This is 8.2's "[ Reload and lose my changes ]", and it is the only way out of `conflicted`
     * that does not create a different document. It also clears `orphaned`, because a file that
     * could be read is by definition no longer missing -- a sync client restoring a folder is an
     * ordinary event, not one that should require closing and reopening the document.
     */
    fun reloaded(contents: DocumentContents): DocumentSessionState =
        copy(
            base = contents.facts,
            hasUnsavedEdits = false,
            writable = contents.writable,
            changedOnDiskTo = null,
            unreachable = false,
        )

    companion object {
        /** 8.2's "At open, record `baseDigest` (SHA-256 of file contents), size, and mtime." */
        fun opened(
            ref: DocumentRef,
            contents: DocumentContents,
        ): DocumentSessionState = DocumentSessionState(ref = ref, base = contents.facts, writable = contents.writable)
    }
}
