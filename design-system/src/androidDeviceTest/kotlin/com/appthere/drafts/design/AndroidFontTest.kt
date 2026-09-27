package com.appthere.drafts.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The fonts, on a real Android device.
 *
 * The Phase 3 acceptance criteria ask for non-Latin text to render "on every target". Two separate
 * things have to hold, and only one of them is checkable here.
 *
 * What *is* checked: the platform's own fallback covers the scripts Atkinson does not. Nothing is
 * bundled behind Atkinson (see `Typeface.kt`), so those glyphs come from `/system/fonts` -- a
 * property of the device rather than of this project, and one that would turn into tofu without a
 * line of our code changing.
 *
 * What is *not*: whether the bundled fonts are readable from the APK. They should be testable here
 * -- this is the one place with both an Android `Context` and the artifact -- but the Compose
 * Resources plugin configures no output directory for the device-test variant's copy-to-assets
 * task, so it fails validation, and with the task disabled the assets arrive empty. The assertion
 * belongs here and cannot live here yet; `FontResourceTest` covers the JVM artifact meanwhile.
 */
class AndroidFontTest {
    @Test
    fun thePlatformCoversTheScriptsAtkinsonDoesNot() {
        // 5.1 names the gap: "It does **not** cover CJK, Arabic, Hebrew, Devanagari, Thai". Nothing
        // is bundled behind Atkinson, so these have to come from the device -- and if a future
        // Android ever stopped shipping them, the fallback would silently become tofu.
        val fonts =
            File("/system/fonts")
                .list()
                .orEmpty()
                .joinToString(" ")
                .lowercase()

        listOf("cjk", "arabic", "devanagari", "hebrew", "thai").forEach { script ->
            assertTrue(script in fonts, "The device ships no $script font for the fallback to reach")
        }
    }
}
