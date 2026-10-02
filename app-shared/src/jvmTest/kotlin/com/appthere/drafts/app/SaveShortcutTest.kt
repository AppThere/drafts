package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.close
import com.appthere.drafts.i18n.resources.could_not_save
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.WriteOutcome
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The keys that save, and what the window says when a save does not happen (7.4, 8.2).
 *
 * The host's *Save As* is stood in for by a counter: the platform's picker is the host's business,
 * and what is tested here is that the window asks for it when it should and not when it should not.
 */
@OptIn(ExperimentalTestApi::class)
class SaveShortcutTest {
    @Test
    fun `saving an untitled document asks where`() =
        runSkikoComposeUiTest(size = SIZE) {
            var asked = 0
            setContent {
                DraftsApp(document = openUntitled(FakeDocumentStore(REF, ORIGINAL), DRAFT), saveAs = {
                    asked++
                    null
                })
            }
            waitForIdle()

            press(Key.S)

            waitUntil(timeoutMillis = TIMEOUT) { asked == 1 }
        }

    @Test
    fun `saving a document with a file writes it without asking`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(REF, ORIGINAL)
            var asked = 0
            setContent {
                DraftsApp(document = opened(store), saveAs = {
                    asked++
                    null
                })
            }
            waitForIdle()

            press(Key.S)

            waitUntil(timeoutMillis = TIMEOUT) { store.writes == 1 }
            assertEquals(0, asked)
        }

    @Test
    fun `save as asks where even when there is a file`() =
        runSkikoComposeUiTest(size = SIZE) {
            var asked = 0
            setContent {
                DraftsApp(document = opened(FakeDocumentStore(REF, ORIGINAL)), saveAs = {
                    asked++
                    null
                })
            }
            waitForIdle()

            press(Key.S, shift = true)

            waitUntil(timeoutMillis = TIMEOUT) { asked == 1 }
        }

    @Test
    fun `a save that fails is said, and goes when the reader closes it`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(REF, ORIGINAL).apply { failsToWrite = true }
            setContent { DraftsApp(document = opened(store)) }
            waitForIdle()

            press(Key.S)
            waitUntil(timeoutMillis = TIMEOUT) { saying() }

            onNodeWithContentDescription(words(Res.string.close)).performClick()
            waitUntil(timeoutMillis = TIMEOUT) { !saying() }
        }

    private fun SkikoComposeUiTest.press(
        key: Key,
        shift: Boolean = false,
    ) {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(key)
            if (shift) keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
    }

    private fun SkikoComposeUiTest.saying(): Boolean =
        onAllNodesWithContentDescription(words(Res.string.could_not_save)).fetchSemanticsNodes().isNotEmpty()

    private fun opened(store: FakeDocumentStore): OpenDocument =
        runBlocking {
            val contents = store.read(REF)
            OpenDocument(store, EditorState(DocumentSession(contents.text)), DocumentSessionState.opened(REF, contents))
        }

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L
        val REF = DocumentRef("/documents/note.md")
        const val ORIGINAL = "As opened.\n"
        const val DRAFT = "A first draft.\n"
    }
}
