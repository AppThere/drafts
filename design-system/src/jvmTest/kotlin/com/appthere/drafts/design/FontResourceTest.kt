package com.appthere.drafts.design

import com.appthere.drafts.design.resources.Res
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fonts and their licences are actually in the artifact.
 *
 * Both halves matter. A font that is declared but not bundled fails at runtime on whichever
 * platform packages resources differently, and the SIL Open Font License requires the licence text
 * to travel with the fonts -- `appthere-drafts.md` 5.1: "Ship the OFL text in an in-app licences
 * screen." A test is the only thing that notices a resource that quietly stopped being packaged.
 *
 * **This runs on the JVM only, and that is a limitation rather than a choice.** Each target
 * packages resources its own way, so the interesting question is whether the other two work -- and
 * neither can be asked from here:
 *
 * - An Android *host* test has no `Context`, and Compose Resources reads through one. It fails with
 *   "Android context is not initialized" whatever the artifact contains.
 * - An Android *device* test can read resources in principle, but the Compose Resources plugin
 *   configures no output directory for the device-test variant's copy-to-assets task, so the task
 *   fails validation and the assets arrive empty. `AndroidFontTest` records what is verifiable on
 *   a device without them.
 * - iOS runs nowhere in this project yet; `check` compiles the Apple targets and stops.
 */
class FontResourceTest {
    @Test
    fun `every bundled font is a real TrueType file`() =
        runTest {
            val fonts =
                listOf(
                    "font/atkinson_next.ttf",
                    "font/atkinson_next_italic.ttf",
                    "font/atkinson_mono.ttf",
                    "font/atkinson_mono_italic.ttf",
                )

            fonts.forEach { path ->
                val bytes = Res.readBytes(path)

                assertTrue(bytes.size > MINIMUM_FONT_BYTES, "$path is only ${bytes.size} bytes")
                assertEquals(
                    TRUETYPE_SIGNATURE,
                    bytes.take(TRUETYPE_SIGNATURE.size),
                    "$path does not start with the TrueType signature",
                )
            }
        }

    @Test
    fun `each family ships its own licence and its own copyright`() =
        runTest {
            // Not decoration: section 4 of the OFL requires the licence to be distributed with the
            // fonts. The two bodies are identical and the copyright lines are not, so each family
            // carries its own -- and dropping one would drop an attribution.
            FontLicences.all.forEach { licence ->
                val text = FontLicences.textOf(licence)

                assertTrue("SIL OPEN FONT LICENSE" in text.uppercase(), "${licence.family} has no OFL")
                assertTrue("Version 1.1" in text, "${licence.family} is not on OFL 1.1")
                assertTrue("Copyright" in text, "${licence.family} ships no copyright notice")
            }
        }

    private companion object {
        /** Anything smaller than this is a placeholder or a failed download, not a typeface. */
        const val MINIMUM_FONT_BYTES = 10_000

        /** `0x00010000`, the version tag every TrueType outline font begins with. */
        val TRUETYPE_SIGNATURE = listOf<Byte>(0x00, 0x01, 0x00, 0x00)
    }
}
