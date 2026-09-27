package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import com.appthere.drafts.design.resources.Res

/**
 * A typeface the app ships, and the licence it ships under.
 *
 * `appthere-drafts.md` 5.1: "Ship the OFL text in an in-app licences screen." That is not a
 * courtesy. Section 4 of the SIL Open Font License requires the licence to be distributed with the
 * fonts, and a screen nobody can reach is not distribution.
 */
@Immutable
data class FontLicence(
    val family: String,
    val resourcePath: String,
)

object FontLicences {
    /**
     * Every font in the artifact, each with its own licence file.
     *
     * Two files whose bodies are identical, which is worth a word. The OFL is the same text in
     * both; what differs is the copyright line naming the project the font came from. Showing one
     * and claiming it covers both would drop an attribution the licence requires, so both are
     * shipped and both are shown.
     */
    val all =
        listOf(
            FontLicence("Atkinson Hyperlegible Next", "files/ofl_atkinson_next.txt"),
            FontLicence("Atkinson Hyperlegible Mono", "files/ofl_atkinson_mono.txt"),
        )

    /** The licence text, read from the artifact rather than repeated in the source. */
    suspend fun textOf(licence: FontLicence): String = Res.readBytes(licence.resourcePath).decodeToString()
}
