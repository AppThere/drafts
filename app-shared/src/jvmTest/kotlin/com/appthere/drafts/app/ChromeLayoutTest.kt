package com.appthere.drafts.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.kind
import com.appthere.drafts.i18n.resources.kind_fountain
import com.appthere.drafts.i18n.resources.kind_markdown
import com.appthere.drafts.i18n.resources.open_outline
import com.appthere.drafts.i18n.resources.open_reader_controls
import com.appthere.drafts.i18n.resources.state_untitled
import com.appthere.drafts.platform.files.DocumentRef
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The chrome along the top of the page, and the document under it.
 *
 * Two things sit there: the document's own status at the start -- 8.4's badge, and 7.4's kind while
 * untitled -- and the window's panel buttons at the end. Measured on a 412dp Chromebook window when
 * they were words: the kind switch ran into the outline button, and the badge sat on the title.
 *
 * Untitled throughout, because that is the widest the start of the bar gets.
 */
@OptIn(ExperimentalTestApi::class)
class ChromeLayoutTest {
    @Test
    fun `on a phone the document's status and the panel buttons do not overlap`() =
        runSkikoComposeUiTest(size = PHONE) {
            open()

            assertApart(statusBounds(), buttonBounds())
        }

    @Test
    fun `the first line of the document starts below the chrome`() =
        runSkikoComposeUiTest(size = PHONE) {
            open()

            assertBelow(onNodeWithText(TITLE).getBoundsInRoot(), statusBounds() + buttonBounds())
        }

    @Test
    fun `at twice the text size the chrome still overlaps neither itself nor the text`() =
        // 10.2's 200% font scale. Everything in the bar grows, so whatever fitted at 1x by a margin
        // no longer does -- this is the case a fixed allowance for the chrome's height would fail.
        runSkikoComposeUiTest(size = PHONE, density = Density(1f, fontScale = 2f)) {
            open()

            assertApart(statusBounds(), buttonBounds())
            assertBelow(onNodeWithText(TITLE).getBoundsInRoot(), statusBounds() + buttonBounds())
        }

    @Test
    fun `right to left, the status is at the right and still clear of the buttons`() =
        runSkikoComposeUiTest(size = PHONE) {
            open(LayoutDirection.Rtl)

            val status = statusBounds()
            val buttons = buttonBounds()
            assertApart(status, buttons)
            assertTrue(
                status.maxOf { it.right.value } > buttons.maxOf { it.right.value },
                "Right to left, the status should be on the right of the buttons",
            )
        }

    @Test
    fun `on a phone the chrome is one line`() =
        // With the kind and the panel buttons drawn as icons, the whole bar fits a phone's width,
        // so nothing has to drop to a second line at the ordinary text size.
        runSkikoComposeUiTest(size = PHONE) {
            open()

            assertOneLine(statusBounds(), buttonBounds())
        }

    @Test
    fun `on a desktop window the chrome is one line`() =
        // The other half of the fix: on a window with room, nothing should move down. Without this,
        // a bar that always put the buttons on a second line would pass every test above.
        runSkikoComposeUiTest(size = DESKTOP) {
            open()

            assertOneLine(statusBounds(), buttonBounds())
        }

    private fun SkikoComposeUiTest.open(direction: LayoutDirection = LayoutDirection.Ltr) {
        val document = openUntitled(FakeDocumentStore(REF, ""), "$TITLE\n\nA second paragraph.\n")
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                DraftsApp(document = document, onKindChange = {})
            }
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.statusBounds(): List<DpRect> =
        listOf(
            onNodeWithText(words(Res.string.state_untitled), useUnmergedTree = true).getBoundsInRoot(),
            onNodeWithContentDescription(MARKDOWN).getBoundsInRoot(),
            onNodeWithContentDescription(FOUNTAIN).getBoundsInRoot(),
        )

    private fun SkikoComposeUiTest.buttonBounds(): List<DpRect> =
        listOf(
            onNodeWithContentDescription(words(Res.string.open_outline)).getBoundsInRoot(),
            onNodeWithContentDescription(words(Res.string.open_reader_controls)).getBoundsInRoot(),
        )

    private fun assertApart(
        status: List<DpRect>,
        buttons: List<DpRect>,
    ) {
        for (a in status) {
            for (b in buttons) {
                assertTrue(!a.overlaps(b), "The status at $a overlaps a panel button at $b")
            }
        }
    }

    private fun assertBelow(
        text: DpRect,
        chrome: List<DpRect>,
    ) {
        val chromeBottom = chrome.maxOf { it.bottom.value }
        assertTrue(
            text.top.value >= chromeBottom,
            "The first line starts at ${text.top}, under chrome that reaches down to $chromeBottom",
        )
    }

    private fun assertOneLine(
        status: List<DpRect>,
        buttons: List<DpRect>,
    ) {
        val sameLine =
            buttons.minOf { it.top.value } < status.maxOf { it.bottom.value } &&
                status.minOf { it.top.value } < buttons.maxOf { it.bottom.value }
        assertTrue(sameLine, "The buttons at $buttons went to another line from the status at $status")
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private companion object {
        /** A phone, and near enough the 412dp Chromebook window this was found on. */
        val PHONE = Size(411f, 891f)
        val DESKTOP = Size(1400f, 1000f)

        val REF = DocumentRef("/documents/untitled.md")
        const val TITLE = "Chapter One"

        val MARKDOWN = "${words(Res.string.kind)}, ${words(Res.string.kind_markdown)}"
        val FOUNTAIN = "${words(Res.string.kind)}, ${words(Res.string.kind_fountain)}"
    }
}
