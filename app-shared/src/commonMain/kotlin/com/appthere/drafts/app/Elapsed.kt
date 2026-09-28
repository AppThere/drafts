package com.appthere.drafts.app

import kotlin.time.TimeSource

/**
 * Milliseconds since this process started.
 *
 * Monotonic, not a wall clock. A wall clock jumps -- NTP corrections, daylight saving, someone
 * setting the date -- and a jump backwards would park 8.1's deadlines in the future and stop
 * autosave until the clock caught up. The schedule only ever measures differences, so an arbitrary
 * origin costs nothing.
 */
internal object Elapsed {
    private val origin = TimeSource.Monotonic.markNow()

    fun millis(): Long = origin.elapsedNow().inWholeMilliseconds
}
