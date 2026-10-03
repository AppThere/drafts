package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.new_document
import com.appthere.drafts.i18n.resources.open_document
import com.appthere.drafts.i18n.resources.open_reader_controls
import com.appthere.drafts.i18n.resources.outline
import com.appthere.drafts.platform.files.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 7.1's other windows, as the window offers them: New and Open in the reader controls -- the one
 * place 12 leaves for them -- and on Ctrl+N and Ctrl+O. The host does them; these check that the
 * window asks, and asks only a host that can.
 */
@OptIn(ExperimentalTestApi::class)
class HostActionsTest {
    @Test
    fun `new and open are offered in the reader controls and ask the host`() =
        runSkikoComposeUiTest(size = SIZE) {
            val asked = mutableListOf<String>()
            open(HostActions(newDocument = { asked += "new" }, openDocument = { asked += "open" }))

            controls()
            onNodeWithContentDescription(NEW).performScrollTo().performClick()
            controls()
            onNodeWithContentDescription(OPEN).performScrollTo().performClick()

            assertEquals(listOf("new", "open"), asked)
        }

    @Test
    fun `choosing one closes the controls, since the reader is going to another window`() =
        runSkikoComposeUiTest(size = SIZE) {
            open(HostActions(newDocument = {}))

            controls()
            onNodeWithContentDescription(NEW).performScrollTo().performClick()

            onNodeWithContentDescription(NEW).assertDoesNotExist()
        }

    @Test
    fun `a host that cannot open windows is offered neither`() =
        runSkikoComposeUiTest(size = SIZE) {
            open(HostActions())

            controls()

            onNodeWithContentDescription(NEW).assertDoesNotExist()
            onNodeWithContentDescription(OPEN).assertDoesNotExist()
        }

    @Test
    fun `ctrl+n and ctrl+o ask the host`() =
        runSkikoComposeUiTest(size = SIZE) {
            val asked = mutableListOf<String>()
            open(HostActions(newDocument = { asked += "new" }, openDocument = { asked += "open" }))

            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.N) } }
            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.O) } }

            assertEquals(listOf("new", "open"), asked)
        }

    @Test
    fun `ctrl+shift+o is still the outline, not open`() =
        runSkikoComposeUiTest(size = SIZE) {
            val asked = mutableListOf<String>()
            open(HostActions(openDocument = { asked += "open" }))

            onRoot().performKeyInput {
                withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.O) } }
            }

            assertEquals(emptyList(), asked)
            onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, words(Res.string.outline)),
            ).assertExists()
        }

    private fun SkikoComposeUiTest.open(host: HostActions) {
        val document = openUntitled(FakeDocumentStore(REF, ""), "# A heading\n\nA paragraph.\n")
        setContent { DraftsApp(document = document, host = host) }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.controls() {
        onNodeWithContentDescription(words(Res.string.open_reader_controls)).performClick()
        waitForIdle()
    }

    private companion object {
        val SIZE = Size(1200f, 900f)
        val REF = DocumentRef("/documents/untitled.md")
        val NEW = words(Res.string.new_document)
        val OPEN = words(Res.string.open_document)
    }
}
