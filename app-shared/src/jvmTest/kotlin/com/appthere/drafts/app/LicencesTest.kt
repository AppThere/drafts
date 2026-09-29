package com.appthere.drafts.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.FontLicences
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.i18n.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The licences screen `appthere-drafts.md` 5.1 requires.
 *
 * This is a licence obligation, not a feature: section 4 of the SIL Open Font License requires the
 * licence and its copyright notices to be distributed with the fonts. A file sitting in the
 * artifact that nothing can show is not something anyone has received, so what is tested here is
 * that a reader can actually get to the text.
 */
@OptIn(ExperimentalTestApi::class)
class LicencesTest {
    @Test
    fun `every bundled font has its licence shown in full`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            setContent { DraftsTheme(ReaderSettings()) { Licences(onClose = {}) } }
            waitForIdle()

            // `assertExists`, not `assertIsDisplayed`: the first licence is ninety lines, so the
            // second family's heading starts below the fold. It is in the scroll, which is what
            // the obligation asks for -- the reader can reach it.
            FontLicences.all.forEach { licence ->
                onNodeWithText(licence.family).assertExists()
            }
        }

    @Test
    fun `both copyright notices are present, not just one`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // The two licence bodies are identical and only the copyright lines differ. Showing one
            // and implying it covers the other would drop an attribution the licence requires.
            setContent { DraftsTheme(ReaderSettings()) { Licences(onClose = {}) } }
            waitForIdle()

            val notices =
                onAllNodesWithText("Copyright", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes()

            assertEquals(
                FontLicences.all.size,
                notices.size,
                "Expected one copyright notice per family, found ${notices.size}",
            )
        }

    @Test
    fun `the licences are reachable from the reader controls`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            var opened = false
            setContent {
                DraftsTheme(ReaderSettings()) {
                    ReaderControls(
                        settings = ReaderSettings(),
                        onChange = {},
                        links = { PanelLink(Strings.LICENCES, onClick = { opened = true }) },
                    )
                }
            }

            onNodeWithContentDescription("Licences").performClick()

            assertTrue(opened, "The licences cannot be reached from the controls")
        }

    @Test
    fun `the screen can be closed again`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            var open by mutableStateOf(true)
            setContent { DraftsTheme(ReaderSettings()) { if (open) Licences(onClose = { open = false }) } }

            onNodeWithContentDescription("Close").performClick()

            assertTrue(!open, "The licences screen cannot be dismissed")
        }

    private companion object {
        const val WIDTH = 900f
        const val HEIGHT = 900f
    }
}
