package com.appthere.drafts.app

import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.allStringResources
import com.appthere.drafts.i18n.resources.file_gone_nothing_kept
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The words as the reader sees them, after Compose Resources has read them out of `strings.xml`.
 *
 * Android resource syntax escapes an apostrophe as `\'`, and `strings.xml` said to. Compose
 * Resources does not unescape it: the reader was shown the backslash, as "This document\'s file is
 * no longer there" on a Chromebook (2026-10-04). Nothing caught it, because nothing read the
 * strings the way a screen does.
 */
class StringsTest {
    @Test
    fun `a message with an apostrophe shows the apostrophe and not an escape`() {
        assertEquals(
            "This document's file is no longer there, and no copy of its words was kept here.",
            words(Res.string.file_gone_nothing_kept),
        )
    }

    @Test
    fun `no string shows the reader a backslash escape`() {
        // Every string, not one: the next apostrophe or quote is the one that would slip through.
        val escaped =
            Res.allStringResources
                .mapValues { (_, resource) -> words(resource) }
                .filterValues { text -> ESCAPES.any { it in text } }

        assertTrue(escaped.isEmpty(), "Strings showing an escape instead of the character: $escaped")
    }

    private companion object {
        val ESCAPES = listOf("\\'", "\\\"", "\\@", "\\?")
    }
}
