package com.appthere.drafts.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appthere.drafts.platform.files.WriteOutcome

/**
 * Saving one document, and what the reader is told about how it went.
 *
 * 8.2's save and 7.4's *Save As* are two ways to the same three outcomes: the words are in the file;
 * 8.2 refused because the file changed underneath; or the write failed -- a full disk, a dropped
 * share, a folder the reader cannot write to. Each is settled here, in one place, so the two ways in
 * cannot disagree about what a refusal or a failure means.
 *
 * A failure is said out loud. It used to be silent: the snapshot kept the words safe, but the reader
 * believed they were in a file.
 */
@Stable
internal class Saving(
    private val document: OpenDocument,
    private val keeper: SnapshotKeeper?,
) {
    /** 8.2's refusal, until the reader answers the dialog it opens. */
    var refusal: WriteOutcome.Conflict? by mutableStateOf(null)
        private set

    /** True while the reader has not yet been told, or has not dismissed, that a save failed. */
    var failed: Boolean by mutableStateOf(false)
        private set

    /**
     * Ctrl+S. A document with no file to save to -- untitled, or one whose file vanished -- is
     * saved with [saveAs]: 7.4, "The first save is *Save As*".
     */
    suspend fun save(saveAs: (suspend () -> WriteOutcome?)?) =
        settle(if (document.needsSaveAs) saveAs?.invoke() else document.save())

    /** Ctrl+Shift+S, or the first save of an untitled document. Null from [saveAs] is a cancel. */
    suspend fun saveAs(saveAs: (suspend () -> WriteOutcome?)?) = settle(saveAs?.invoke())

    /** The conflict dialog has been answered, whichever way. */
    fun answered() {
        refusal = null
    }

    /** The reader has read that the save failed. */
    fun acknowledged() {
        failed = false
    }

    private suspend fun settle(outcome: WriteOutcome?) {
        when (outcome) {
            // Cancelled, or nowhere to save to. Nothing was attempted, so there is nothing to say.
            null -> {
                Unit
            }

            is WriteOutcome.Written -> {
                refusal = null
                failed = false

                // 8.3's thirty days are counted from here. Only on a write that happened: stamping a
                // refused or failed save would make the snapshot prunable while it was still the
                // only copy of the work.
                keeper?.noteSaved()
            }

            is WriteOutcome.Conflict -> {
                refusal = outcome
            }

            is WriteOutcome.Unavailable -> {
                failed = true
            }
        }
    }
}
