package com.appthere.drafts.project

import kotlin.test.Test
import kotlin.test.assertEquals

/** The trash's folder names: UTC, readable in a file manager, sorting in time order. */
class TrashStampTest {
    @Test
    fun `a moment is written as its UTC date and time`() {
        assertEquals("2026-09-21T141320Z", stamp(1_790_000_000_000L))
    }

    @Test
    fun `a leap day is a day like any other`() {
        assertEquals("2000-02-29T000000Z", stamp(951_782_400_000L))
    }
}
