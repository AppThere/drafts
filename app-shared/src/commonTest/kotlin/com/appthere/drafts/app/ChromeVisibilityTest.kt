package com.appthere.drafts.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 12's auto-hide, at explicit instants.
 *
 * "On sustained typing, toolbars and rails fade out. Any pointer movement, keypress of a modifier,
 * or edge gesture brings them back."
 */
class ChromeVisibilityTest {
    @Test
    fun `chrome is visible on a document nobody is typing into`() {
        assertFalse(ChromeVisibility().isHidden(START))
    }

    @Test
    fun `one keystroke is not sustained typing`() {
        // Correcting a word must not rearrange the interface around the reader.
        val chrome = ChromeVisibility()
        chrome.typed(START)

        assertFalse(chrome.isHidden(START))
    }

    @Test
    fun `typing for long enough hides the chrome`() {
        val chrome = ChromeVisibility()
        chrome.typed(START)

        assertTrue(chrome.isHidden(START + ChromeVisibility.SUSTAINED_AFTER_MILLIS))
    }

    @Test
    fun `just under the threshold does not`() {
        // The boundary is the rule. A test a second past it would pass against something that hid
        // the chrome on the first keystroke.
        val chrome = ChromeVisibility()
        chrome.typed(START)

        assertFalse(chrome.isHidden(START + ChromeVisibility.SUSTAINED_AFTER_MILLIS - 1))
    }

    @Test
    fun `typing is measured from when it started rather than the last keystroke`() {
        // Otherwise every keystroke would restart the clock and the chrome would never hide for
        // the one reader it exists for -- the one who does not stop.
        val chrome = ChromeVisibility()
        chrome.typed(START)
        chrome.typed(START + ChromeVisibility.SUSTAINED_AFTER_MILLIS - 1)

        assertTrue(chrome.isHidden(START + ChromeVisibility.SUSTAINED_AFTER_MILLIS))
    }

    @Test
    fun `reaching for something brings it back`() {
        val chrome = ChromeVisibility()
        chrome.typed(START)

        chrome.roused()

        assertFalse(chrome.isHidden(START + ChromeVisibility.SUSTAINED_AFTER_MILLIS))
    }

    @Test
    fun `it hides again once typing resumes`() {
        val chrome = ChromeVisibility()
        chrome.typed(START)
        chrome.roused()

        chrome.typed(START + ONE_SECOND)

        assertTrue(chrome.isHidden(START + ONE_SECOND + ChromeVisibility.SUSTAINED_AFTER_MILLIS))
    }

    @Test
    fun `pausing does not bring it back`() {
        // 12 lists three ways back and stopping is not one of them. A writer who pauses to think
        // has not asked for the furniture, and returning it then is the interface fidgeting at the
        // moment they were concentrating.
        val chrome = ChromeVisibility()
        chrome.typed(START)

        assertTrue(chrome.isHidden(START + AN_HOUR))
    }

    @Test
    fun `the deadline it reports is the moment it hides`() {
        // So a caller can sleep until then instead of polling once a frame.
        val chrome = ChromeVisibility()
        chrome.typed(START)
        val deadline = requireNotNull(chrome.hidesAt())

        assertFalse(chrome.isHidden(deadline - 1))
        assertTrue(chrome.isHidden(deadline))
    }

    @Test
    fun `there is no deadline when nobody is typing`() {
        assertNull(ChromeVisibility().hidesAt())

        val chrome = ChromeVisibility()
        chrome.typed(START)
        chrome.roused()
        assertNull(chrome.hidesAt())
    }

    @Test
    fun `the threshold is a line of prose rather than a keystroke`() {
        // Guards the number itself. Anything under half a second would fire while someone was
        // still finding the word; anything over a few seconds would never fire at all.
        assertTrue(ChromeVisibility.SUSTAINED_AFTER_MILLIS in FLOOR..CEILING)
        assertEquals(1_500L, ChromeVisibility.SUSTAINED_AFTER_MILLIS)
    }

    private companion object {
        const val START = 1_000_000L
        const val ONE_SECOND = 1_000L
        const val AN_HOUR = 3_600_000L
        const val FLOOR = 500L
        const val CEILING = 4_000L
    }
}
