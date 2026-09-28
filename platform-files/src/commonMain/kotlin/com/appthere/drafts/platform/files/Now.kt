package com.appthere.drafts.platform.files

/**
 * Milliseconds since the Unix epoch.
 *
 * A wall clock, deliberately, and the only place in the project that reads one. 8.3's retention is
 * "30 days after a successful save", which is measured across restarts -- and a monotonic source
 * resets when the process does, so thirty days would never elapse.
 *
 * Timing *policy* still takes its instant from the caller. `SnapshotSchedule` reads no clock at
 * all, which is what lets 8.1's rules be tested at an explicit moment rather than by waiting.
 */
expect fun epochMillis(): Long
