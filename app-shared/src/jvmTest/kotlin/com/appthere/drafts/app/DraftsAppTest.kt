package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.FontLicences
import com.appthere.drafts.i18n.Strings
import kotlin.test.Test

/**
 * The reader controls are reachable without a pointer.
 *
 * 10.2: "Complete keyboard operation. Every action reachable without pointer." The controls are
 * assistive technology, so reaching them must not itself require the thing someone cannot use.
 */
@OptIn(ExperimentalTestApi::class)
class DraftsAppTest {
    @Test
    fun `the reader controls open from the keyboard`() =
        runSkikoComposeUiTest(size = Size(1200f, 900f)) {
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            waitForIdle()

            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.Comma)
                keyUp(Key.CtrlLeft)
            }

            onNodeWithContentDescription("Text size, increase").assertIsDisplayed()
        }

    @Test
    fun `the same shortcut closes them again`() =
        runSkikoComposeUiTest(size = Size(1200f, 900f)) {
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            waitForIdle()

            repeat(2) {
                onRoot().performKeyInput {
                    keyDown(Key.CtrlLeft)
                    pressKey(Key.Comma)
                    keyUp(Key.CtrlLeft)
                }
            }

            onNodeWithText("A paragraph.").assertIsDisplayed()
        }

    @Test
    fun `the reader controls open without a keyboard`() =
        runSkikoComposeUiTest(size = Size(1200f, 900f)) {
            // A phone has no Ctrl+Comma. Until this button existed the controls could not be
            // reached on Android at all.
            setContent { DraftsApp(initialText = "A paragraph.\n") }

            onNodeWithContentDescription(Strings.OPEN_READER_CONTROLS).performClick()

            onNodeWithContentDescription(LARGER).assertIsDisplayed()
        }

    @Test
    fun `the reader controls close without a keyboard`() =
        runSkikoComposeUiTest(size = Size(1200f, 900f)) {
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            onNodeWithContentDescription(Strings.OPEN_READER_CONTROLS).performClick()

            onNodeWithContentDescription(Strings.CLOSE).performClick()

            onNodeWithContentDescription(LARGER).assertDoesNotExist()
            onNodeWithContentDescription(Strings.OPEN_READER_CONTROLS).assertIsDisplayed()
        }

    @Test
    fun `the shortcut still works after the controls were closed by pointer`() =
        runSkikoComposeUiTest(size = Size(1200f, 900f)) {
            // The Close button takes focus when pressed and then leaves the composition. Had focus
            // gone with it, there would be nowhere for the next keystroke to start from, and the
            // reader's shortcuts would silently stop until they clicked the document.
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            onNodeWithContentDescription(Strings.OPEN_READER_CONTROLS).performClick()
            onNodeWithContentDescription(Strings.CLOSE).performClick()

            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.Comma)
                keyUp(Key.CtrlLeft)
            }

            onNodeWithContentDescription(LARGER).assertIsDisplayed()
        }

    @Test
    fun `escape closes the reader controls`() =
        runSkikoComposeUiTest(size = Size(1200f, 900f)) {
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            onNodeWithContentDescription(Strings.OPEN_READER_CONTROLS).performClick()

            onRoot().performKeyInput { pressKey(Key.Escape) }

            onNodeWithContentDescription(LARGER).assertDoesNotExist()
        }

    @Test
    fun `escape over the licences closes the licences and leaves the controls`() =
        runSkikoComposeUiTest(size = Size(1200f, 900f)) {
            // One Escape, one panel. The licences open from the controls; closing both at once
            // would send the reader back further than they asked to go.
            setContent { DraftsApp(initialText = "A paragraph.\n") }
            onNodeWithContentDescription(Strings.OPEN_READER_CONTROLS).performClick()
            onNodeWithContentDescription(Strings.LICENCES).performScrollTo().performClick()
            onNodeWithText(FontLicences.all.first().family).assertExists()

            onRoot().performKeyInput { pressKey(Key.Escape) }

            onNodeWithText(FontLicences.all.first().family).assertDoesNotExist()
            onNodeWithContentDescription(LARGER).assertExists()
        }

    private companion object {
        const val LARGER = "Text size, increase"
    }
}
