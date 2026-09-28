package com.appthere.drafts.platform.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 8.1's two timing rules, at explicit timestamps.
 *
 * The rules read as though one subsumes the other, and they do not: "3 seconds of idle after an
 * edit" protects the reader who pauses, and "30 seconds of continuous editing since last snapshot"
 * protects the one who never does. Someone typing steadily for a minute never has three idle
 * seconds, and without the second rule their work would sit unsnapshotted the whole time -- which
 * is exactly the reader 8.1 exists for.
 */
class SnapshotScheduleTest {
    @Test
    fun `a document nobody has touched has nothing to snapshot`() {
        val schedule = SnapshotSchedule()

        assertFalse(schedule.hasUnsavedEdits)
        assertNull(schedule.dueAt(START + HOUR))
        assertNull(schedule.nextDueAt())
    }

    @Test
    fun `three seconds of idle after an edit is due`() {
        val schedule = SnapshotSchedule()
        schedule.edited(START)

        assertEquals(SnapshotTrigger.Idle, schedule.dueAt(START + SnapshotSchedule.IDLE_AFTER_MILLIS))
    }

    @Test
    fun `just under three seconds is not due`() {
        // The boundary is the rule. A test at four seconds would pass against an implementation
        // that fired at one, which would snapshot on every keystroke pause.
        val schedule = SnapshotSchedule()
        schedule.edited(START)

        assertNull(schedule.dueAt(START + SnapshotSchedule.IDLE_AFTER_MILLIS - 1))
    }

    @Test
    fun `typing keeps pushing the idle deadline back`() {
        // What "idle" means. A reader mid-sentence has not stopped, however long they have been
        // going, and snapshotting between two keystrokes buys nothing the next one does not undo.
        val schedule = SnapshotSchedule()
        schedule.edited(START)
        schedule.edited(START + SECOND)
        schedule.edited(START + SECOND * 2)

        assertNull(schedule.dueAt(START + SECOND * 2 + SnapshotSchedule.IDLE_AFTER_MILLIS - 1))
        assertEquals(
            SnapshotTrigger.Idle,
            schedule.dueAt(START + SECOND * 2 + SnapshotSchedule.IDLE_AFTER_MILLIS),
        )
    }

    @Test
    fun `continuous typing is snapshotted after thirty seconds anyway`() {
        // The rule the idle rule cannot cover. An edit every second means the idle deadline is never
        // reached, so without this the document would go unsnapshotted for as long as the reader
        // keeps typing -- which on a good day is an hour.
        val schedule = SnapshotSchedule()
        var now = START
        while (now <= START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS) {
            schedule.edited(now)
            now += SECOND
        }

        assertEquals(
            SnapshotTrigger.ContinuousEditing,
            schedule.dueAt(START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS),
        )
    }

    @Test
    fun `continuous editing is measured from the last snapshot and not the last keystroke`() {
        // 8.1 says "since last snapshot". Restarting the clock on every edit would mean the
        // thirty-second rule never fires for a reader who types at least once every thirty seconds
        // -- that is, for everyone it was written to protect.
        val schedule = SnapshotSchedule()
        schedule.edited(START)
        schedule.edited(START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS - SECOND)

        assertEquals(
            SnapshotTrigger.ContinuousEditing,
            schedule.dueAt(START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS),
        )
    }

    @Test
    fun `a snapshot clears what was pending`() {
        val schedule = SnapshotSchedule()
        schedule.edited(START)

        schedule.snapshotted()

        assertFalse(schedule.hasUnsavedEdits)
        assertNull(schedule.dueAt(START + HOUR))
    }

    @Test
    fun `the thirty second clock restarts after a snapshot`() {
        // Otherwise the second snapshot would be due the instant the first finished, and a reader
        // who keeps typing would be snapshotted on every tick for the rest of the session.
        val schedule = SnapshotSchedule()
        schedule.edited(START)
        schedule.snapshotted()

        schedule.edited(START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS)

        assertNull(schedule.dueAt(START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS + SECOND))
    }

    @Test
    fun `the next deadline is the sooner of the two rules`() {
        // So a caller can sleep until then instead of polling. Waking early is wasteful; waking
        // late is a snapshot that did not happen when 8.1 said it should.
        val schedule = SnapshotSchedule()
        schedule.edited(START)

        assertEquals(START + SnapshotSchedule.IDLE_AFTER_MILLIS, schedule.nextDueAt())
    }

    @Test
    fun `the next deadline follows the thirty second rule once idle is out of reach`() {
        val schedule = SnapshotSchedule()
        schedule.edited(START)
        val lastEdit = START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS - SECOND
        schedule.edited(lastEdit)

        assertEquals(START + SnapshotSchedule.CONTINUOUS_AFTER_MILLIS, schedule.nextDueAt())
    }

    @Test
    fun `the deadline it reports is the moment something becomes due`() {
        // Ties the two halves together. A `nextDueAt` that disagreed with `dueAt` would have a
        // caller sleeping until a moment at which nothing happens, and then sleeping again.
        val schedule = SnapshotSchedule()
        schedule.edited(START)
        val deadline = requireNotNull(schedule.nextDueAt())

        assertNull(schedule.dueAt(deadline - 1))
        assertTrue(schedule.dueAt(deadline) != null, "Nothing was due at the deadline it named")
    }

    private companion object {
        const val START = 1_000_000L
        const val SECOND = 1_000L
        const val HOUR = 3_600_000L
    }
}
