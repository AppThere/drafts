package com.appthere.drafts.design

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Lucide's ISC notice travels with the icons copied from it, as the licence asks. */
class LucideLicenceTest {
    @Test
    fun `the icons ship lucide's isc notice and its copyright`() =
        runTest {
            BundledLicences.icons.forEach { licence ->
                val text = BundledLicences.textOf(licence)

                assertTrue("ISC License" in text, "${licence.name} ships no ISC licence")
                assertTrue("Copyright" in text && "Lucide" in text, "${licence.name} ships no copyright notice")
            }
        }
}
