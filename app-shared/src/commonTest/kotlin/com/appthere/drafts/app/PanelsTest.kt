package com.appthere.drafts.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Escape and Android's Back close one panel at a time, the one on top first. */
class PanelsTest {
    @Test
    fun `the licences close before the controls they were opened from`() {
        val panels =
            Panels().apply {
                openControls()
                openLicences()
            }

        assertTrue(panels.closeTopmost())
        assertFalse(panels.licences)
        assertTrue(panels.controls, "Closing the licences closed the controls too")

        assertTrue(panels.closeTopmost())
        assertFalse(panels.anyOpen)
    }

    @Test
    fun `with nothing open there is nothing to close`() {
        // False lets the key go on to whatever else wants it, rather than Escape vanishing.
        assertFalse(Panels().closeTopmost())
    }
}
