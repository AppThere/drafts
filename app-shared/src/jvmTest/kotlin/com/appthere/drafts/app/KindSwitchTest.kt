package com.appthere.drafts.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.kind
import com.appthere.drafts.i18n.resources.kind_fountain
import com.appthere.drafts.i18n.resources.kind_markdown
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.intents.DocumentKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 7.4's kind as a control while untitled -- *Untitled · Markdown* -- and what changing it does. */
@OptIn(ExperimentalTestApi::class)
class KindSwitchTest {
    @Test
    fun `an untitled document shows its kind as a choice`() =
        runSkikoComposeUiTest(size = SIZE) {
            setContent { DraftsApp(document = untitled(), onKindChange = {}) }

            onNodeWithContentDescription(MARKDOWN).assertIsSelected()
        }

    @Test
    fun `choosing fountain asks for fountain`() =
        runSkikoComposeUiTest(size = SIZE) {
            var chosen: DocumentKind? = null
            setContent { DraftsApp(document = untitled(), onKindChange = { chosen = it }) }

            onNodeWithContentDescription(FOUNTAIN).performClick()

            assertEquals(DocumentKind.Fountain, chosen)
        }

    @Test
    fun `a document with a file does not offer a kind`() =
        runSkikoComposeUiTest(size = SIZE) {
            // 9.1: its extension already says what it is.
            setContent { DraftsApp(document = opened(), onKindChange = {}) }
            waitForIdle()

            assertTrue(onAllNodesWithContentDescription(FOUNTAIN).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `the window takes the settings of the kind it becomes`() =
        runSkikoComposeUiTest(size = SIZE) {
            // 5.5 keeps settings per kind. When the host hands over a new kind's, the window wears
            // them now -- it used to keep the old kind's until the document was reopened.
            var settings by mutableStateOf(ReaderSettings())
            setContent { DraftsApp(document = untitled(), initialSettings = settings, onKindChange = {}) }
            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.Comma)
                keyUp(Key.CtrlLeft)
            }

            settings = ReaderSettings(paragraphSpacing = WIDER)
            waitForIdle()

            assertTrue(
                onAllNodesWithText("${WIDER}em").fetchSemanticsNodes().isNotEmpty(),
                "The window kept the old settings",
            )
        }

    private fun untitled() = openUntitled(FakeDocumentStore(REF, ORIGINAL), "A first draft.\n")

    private fun opened(): OpenDocument =
        runBlocking {
            val store = FakeDocumentStore(REF, ORIGINAL)
            val contents = store.read(REF)
            OpenDocument(store, EditorState(DocumentSession(contents.text)), DocumentSessionState.opened(REF, contents))
        }

    private companion object {
        val SIZE = Size(1200f, 900f)
        val REF = DocumentRef("/documents/note.md")
        const val ORIGINAL = "As opened.\n"
        const val WIDER = 1.25f
        val MARKDOWN = "${words(Res.string.kind)}, ${words(Res.string.kind_markdown)}"
        val FOUNTAIN = "${words(Res.string.kind)}, ${words(Res.string.kind_fountain)}"
    }
}
