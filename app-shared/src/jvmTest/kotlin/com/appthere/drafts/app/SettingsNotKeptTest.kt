package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.settings_not_saved
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * A settings file that could not be written, as the reader finds out about it.
 *
 * `SettingsStore.remember` has always answered whether the write happened. Nothing listened, so a
 * reader on a full disk changed their settings, saw them apply, and found them gone next time
 * with no word of why. The panel is where the change was made, so the panel is where it is said.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsNotKeptTest {
    @Test
    fun `a change that could not be kept says so`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(DOCUMENT, TEXT).apply { failsToWrite = true }
            show(store)

            onNodeWithContentDescription(LARGER).performClick()

            waitUntil(timeoutMillis = TIMEOUT) { saying() }
        }

    @Test
    fun `a change that was kept says nothing`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(DOCUMENT, TEXT)
            show(store)

            onNodeWithContentDescription(LARGER).performClick()
            waitForIdle()

            assertFalse(saying(), "A setting that was kept was reported as lost")
        }

    @Test
    fun `the message goes once a later change is kept`() =
        runSkikoComposeUiTest(size = SIZE) {
            // The latest write is the one that says what is on disk. After a successful one the
            // earlier failure no longer matters: everything the reader chose is there.
            val store = FakeDocumentStore(DOCUMENT, TEXT).apply { failsToWrite = true }
            show(store)

            onNodeWithContentDescription(LARGER).performClick()
            waitUntil(timeoutMillis = TIMEOUT) { saying() }

            store.failsToWrite = false
            onNodeWithContentDescription(LARGER).performClick()

            waitUntil(timeoutMillis = TIMEOUT) { !saying() }
        }

    /** A document open with settings kept in [store], and the reader controls showing. */
    private fun SkikoComposeUiTest.show(store: FakeDocumentStore) {
        val document =
            runBlocking {
                val contents = store.read(DOCUMENT)
                OpenDocument(
                    store = store,
                    editor = EditorState(DocumentSession(contents.text)),
                    opened = DocumentSessionState.opened(DOCUMENT, contents),
                )
            }

        setContent { DraftsApp(document = document, settingsStore = SettingsStore(store, SETTINGS)) }
        waitForIdle()

        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Comma)
            keyUp(Key.CtrlLeft)
        }
    }

    private fun SkikoComposeUiTest.saying(): Boolean =
        onAllNodesWithText(words(Res.string.settings_not_saved)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L

        val DOCUMENT = DocumentRef("/documents/note.md")
        const val TEXT = "A paragraph.\n"
        const val SETTINGS = "/settings"

        const val LARGER = "Text size, increase"
    }
}
