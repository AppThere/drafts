package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.appthere.drafts.editor.ui.EditorShortcuts
import com.appthere.drafts.editor.ui.KeyLabel
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.key_ctrl
import com.appthere.drafts.i18n.resources.key_shift
import com.appthere.drafts.i18n.resources.keyboard_shortcuts
import com.appthere.drafts.i18n.resources.open_reader_controls
import com.appthere.drafts.i18n.resources.said_after
import com.appthere.drafts.i18n.resources.shortcut_full_screen
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 10.2: "Document the full shortcut map."
 *
 * The list is reachable from the keyboard and without one, it lists every shortcut the application
 * answers, and no two shortcuts share keys -- which would leave one of them unreachable, silently.
 */
@OptIn(ExperimentalTestApi::class)
class ShortcutListTest {
    @Test
    fun `the list opens from the keyboard and names every shortcut`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            waitForIdle()

            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Slash) } }

            (EditorShortcuts.all + WindowShortcuts.all).forEach { assertListed(it) }
        }

    @Test
    fun `the list opens from the reader controls without a keyboard`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // A tablet with a keyboard case is the reader this list is for, and they may well
            // reach for the screen before they reach for a chord they do not know yet.
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            onNodeWithContentDescription(words(Res.string.open_reader_controls)).performClick()

            onNodeWithContentDescription(words(Res.string.keyboard_shortcuts)).performScrollTo().performClick()

            assertListed(WindowShortcuts.Save)
        }

    @Test
    fun `a shortcut the host answers is listed with the rest`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val hosted = Shortcut(Res.string.shortcut_full_screen, Key.F12, "F12")
            setContent { ShortcutList(onClose = {}, hostShortcuts = listOf(hosted)) }

            assertListed(hosted)
        }

    @Test
    fun `escape closes the list and leaves the controls it was opened from`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            onNodeWithContentDescription(words(Res.string.open_reader_controls)).performClick()
            onNodeWithContentDescription(words(Res.string.keyboard_shortcuts)).performScrollTo().performClick()

            onRoot().performKeyInput { pressKey(Key.Escape) }

            onNodeWithContentDescription(label(WindowShortcuts.Save)).assertDoesNotExist()
            onNodeWithContentDescription("Text size, increase").assertExists()
        }

    @Test
    fun `the same keys close it again`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            waitForIdle()

            repeat(2) { onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Slash) } } }

            onNodeWithContentDescription(label(WindowShortcuts.Save)).assertDoesNotExist()
        }

    @Test
    fun `no two shortcuts share keys`() {
        // Redo is listed twice on purpose, on two different keys. Two *actions* on one set of keys
        // is the mistake: whichever handler asks first wins, and the other is listed but dead.
        val shortcuts = EditorShortcuts.all + WindowShortcuts.all
        val clashes =
            shortcuts
                .groupBy { Triple(it.key, it.primary, it.shift) }
                .filterValues { it.size > 1 }

        assertEquals(emptyMap(), clashes)
    }

    private fun SemanticsNodeInteractionsProvider.assertListed(shortcut: Shortcut) {
        onNodeWithContentDescription(label(shortcut)).performScrollTo()
    }

    /** The row's description, built the way `ShortcutList` builds it. */
    private fun label(shortcut: Shortcut): String {
        val keys =
            listOfNotNull(
                words(Res.string.key_ctrl).takeIf { shortcut.primary },
                words(Res.string.key_shift).takeIf { shortcut.shift },
                when (val of = shortcut.label) {
                    is KeyLabel.Letter -> of.of
                    is KeyLabel.Named -> words(of.word)
                },
            ).joinToString("+")

        return words(Res.string.said_after).replace("%1\$s", keys).replace("%2\$s", words(shortcut.action))
    }

    private companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 900f
    }
}
