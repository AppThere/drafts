package com.appthere.drafts.platform.files

/** Why a snapshot is being taken. `appthere-drafts.md` 8.1 lists five occasions. */
enum class SnapshotTrigger {
    /** "3 seconds of idle after an edit." The common one: the reader stopped to think. */
    Idle,

    /** "30 seconds of continuous editing since last snapshot." For someone who does not stop. */
    ContinuousEditing,

    /** "Application or window loses focus (backgrounded, blurred, scene deactivated)." */
    FocusLost,

    /** "Window close, before teardown." */
    Closing,

    /** "Explicit or implicit navigation away from the document." */
    NavigatedAway,
}

/**
 * When to take a snapshot, as arithmetic over timestamps.
 *
 * No clock, no coroutines, no Compose. The caller says what time it is, which is what makes 8.1's
 * two timing rules testable at all -- a policy that read a clock could only be tested by waiting
 * thirty seconds, and a test nobody will run is not a test.
 *
 * The three event triggers need no logic and are not represented here: focus loss, window close and
 * navigating away are things that happen, and the answer is always "snapshot now if there is
 * anything to snapshot". [hasUnsavedEdits] is the whole of that question.
 */
class SnapshotSchedule(
    private val idleAfterMillis: Long = IDLE_AFTER_MILLIS,
    private val continuousAfterMillis: Long = CONTINUOUS_AFTER_MILLIS,
) {
    /**
     * Edits that no snapshot has captured, or null if there are none.
     *
     * One nullable value rather than two nullable timestamps, because the two are always both set
     * or both clear -- and a type that says so cannot be left half-updated by a method that
     * remembers one of them.
     */
    private var pending: Pending? = null

    /** True when an edit has happened that no snapshot has captured yet. */
    val hasUnsavedEdits: Boolean get() = pending != null

    /**
     * Records an edit at [now].
     *
     * [editingSince] is set on the *first* edit after a snapshot and left alone afterwards, because
     * 8.1 measures continuous editing "since last snapshot" rather than since the last keystroke.
     * Resetting it on every edit would mean the thirty-second rule never fires for the reader it
     * exists for -- the one who is still typing.
     */
    fun edited(now: Long) {
        pending = pending?.copy(lastEdit = now) ?: Pending(editingSince = now, lastEdit = now)
    }

    /** Records that a snapshot captured everything up to [now]. */
    fun snapshotted() {
        pending = null
    }

    /** Which timed trigger, if any, is due at [now]. Idle first: it is the cheaper moment to write. */
    fun dueAt(now: Long): SnapshotTrigger? {
        val unsaved = pending ?: return null

        return when {
            now - unsaved.lastEdit >= idleAfterMillis -> SnapshotTrigger.Idle
            now - unsaved.editingSince >= continuousAfterMillis -> SnapshotTrigger.ContinuousEditing
            else -> null
        }
    }

    /**
     * The earliest time [dueAt] could return something, so a caller can sleep until then rather
     * than poll. Null when there is nothing pending.
     */
    fun nextDueAt(): Long? =
        pending?.let { minOf(it.lastEdit + idleAfterMillis, it.editingSince + continuousAfterMillis) }

    /**
     * [editingSince] is the first edit after the last snapshot and [lastEdit] the most recent one.
     *
     * They are different questions: 8.1 measures idleness from the last keystroke and continuous
     * editing "since last snapshot", so a single timestamp cannot answer both.
     */
    private data class Pending(
        val editingSince: Long,
        val lastEdit: Long,
    )

    companion object {
        /** 8.1: "3 seconds of idle after an edit". */
        const val IDLE_AFTER_MILLIS = 3_000L

        /** 8.1: "30 seconds of continuous editing since last snapshot". */
        const val CONTINUOUS_AFTER_MILLIS = 30_000L
    }
}
