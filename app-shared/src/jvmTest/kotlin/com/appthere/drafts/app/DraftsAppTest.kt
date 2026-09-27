package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
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
}
