package com.appthere.drafts.platform.files

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Apple's wall clock. `NSDate` reports seconds as a double; 8.3 counts days, so this is ample. */
actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

private const val MILLIS_PER_SECOND = 1_000
