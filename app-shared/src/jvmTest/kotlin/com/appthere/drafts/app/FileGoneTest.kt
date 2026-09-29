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
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.DocumentRef
import kotlin.test.Test

/**
 * 7.3's banner for a document whose file vanished, "offering *Save As*", and how it goes away.
 *
 * Save As here is the real `saveAs` on the document, to a location the host would have asked for,
 * so what is tested is the whole route from the banner to a document with a file again.
 */
@OptIn(ExperimentalTestApi::class)
class FileGoneTest {
    @Test
    fun `a document whose file vanished offers save as`() =
        runSkikoComposeUiTest(size = SIZE) {
            setContent { DraftsApp(document = vanished(), saveAs = { null }) }

            waitUntil(timeoutMillis = TIMEOUT) { saying() }
        }

    @Test
    fun `choosing save as gives it a file and the banner goes`() =
        runSkikoComposeUiTest(size = SIZE) {
            val document = vanished()
            setContent { DraftsApp(document = document, saveAs = { document.saveAs(ELSEWHERE) }) }
            waitUntil(timeoutMillis = TIMEOUT) { saying() }

            onNodeWithContentDescription(Strings.SAVE_AS_CHOICE).performClick()

            waitUntil(timeoutMillis = TIMEOUT) { !saying() }
        }

    @Test
    fun `ctrl s on it asks where rather than doing nothing`() =
        runSkikoComposeUiTest(size = SIZE) {
            // There is no file to write to; the save has to choose one.
            var asked = 0
            setContent {
                DraftsApp(document = vanished(), saveAs = {
                    asked++
                    null
                })
            }
            waitForIdle()

            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.S)
                keyUp(Key.CtrlLeft)
            }

            waitUntil(timeoutMillis = TIMEOUT) { asked == 1 }
        }

    @Test
    fun `an untitled document is not told its file vanished`() =
        runSkikoComposeUiTest(size = SIZE) {
            // It never had one. Its state already says so, and a banner saying a file had gone
            // would be false.
            setContent {
                DraftsApp(
                    document = openUntitled(FakeDocumentStore(GONE, ""), "A draft.\n"),
                    saveAs = { null },
                )
            }
            waitForIdle()

            kotlin.test.assertFalse(saying())
        }

    private fun vanished() = openVanished(FakeDocumentStore(ELSEWHERE, ""), GONE, "Kept words.\n")

    private fun SkikoComposeUiTest.saying(): Boolean =
        onAllNodesWithContentDescription(Strings.FILE_GONE).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L
        val GONE = DocumentRef("/documents/gone.md")
        val ELSEWHERE = DocumentRef("/documents/saved-again.md")
    }
}
