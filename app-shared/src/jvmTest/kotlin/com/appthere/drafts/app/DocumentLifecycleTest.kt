package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.PathDocumentStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Section 8 through the whole stack, against a real file.
 *
 * This is the Phase 4 acceptance criterion "Modify the file externally while open; the digest check
 * fires and no write occurs", asserted rather than demonstrated by hand. Every layer is the real one
 * -- `PathDocumentStore` writing to a temporary directory, the composition root's Ctrl+S, the badge
 * 8.4 asks for -- because the parts that break are the joins between them, and each layer's own
 * tests pass with the joins wired wrongly.
 */
@OptIn(ExperimentalTestApi::class)
class DocumentLifecycleTest {
    private val directory: Path = createTempDirectory("drafts-lifecycle")
    private val store = PathDocumentStore()

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a document opened from a file shows as saved`() {
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            waitForIdle()

            onNodeWithContentDescription(badge(Strings.STATE_CLEAN)).assertExists()
        }
    }

    @Test
    fun `typing shows as unsaved`() {
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")

            onNodeWithContentDescription(badge(Strings.STATE_DIRTY)).assertExists()
        }
    }

    @Test
    fun `ctrl-S writes the file and the badge goes back to saved`() {
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")

            save()
            waitUntil(timeoutMillis = TIMEOUT) { file().readText() != ORIGINAL }

            assertEquals("Mine. $ORIGINAL", file().readText())
            onNodeWithContentDescription(badge(Strings.STATE_CLEAN)).assertExists()
        }
    }

    @Test
    fun `a file changed on disk refuses the save and says so in the chrome`() {
        // The criterion. Another editor -- or a sync client -- rewrote the file after it was opened.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            file().writeText(THEIRS)

            save()
            waitUntil(timeoutMillis = TIMEOUT) { conflicted() }

            onNodeWithContentDescription(badge(Strings.STATE_CONFLICTED)).assertExists()
        }
    }

    @Test
    fun `the refused save leaves the other version on disk untouched`() {
        // "and no write occurs". The badge could say the right thing while the bytes had already
        // gone, so the file itself has to be the assertion.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            file().writeText(THEIRS)

            save()
            waitUntil(timeoutMillis = TIMEOUT) { conflicted() }

            assertEquals(THEIRS, file().readText(), "The refused save overwrote the other version")
        }
    }

    @Test
    fun `the deleted file shows as missing rather than silently reappearing`() {
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            Files.delete(file())

            save()
            waitUntil(timeoutMillis = TIMEOUT) {
                onAllNodesWithContentDescription(badge(Strings.STATE_ORPHANED)).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithContentDescription(badge(Strings.STATE_ORPHANED)).assertExists()
        }
    }

    @Test
    fun `opening through the store reaches the document`() {
        // The composable path the desktop entry point actually uses, rather than the constructor the
        // tests above reach for. If `rememberOpenDocument` never produced an `Opened`, the window
        // would sit on "Opening" forever and nothing else here would notice.
        runSkikoComposeUiTest(size = SIZE) {
            val ref = write(ORIGINAL)
            setContent {
                val opening = rememberOpenDocument(store, ref)
                if (opening is DocumentOpening.Opened) {
                    DraftsApp(document = opening.document)
                }
            }
            waitUntil(timeoutMillis = TIMEOUT) {
                onAllNodesWithText(FIRST_LINE).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithContentDescription(badge(Strings.STATE_CLEAN)).assertExists()
        }
    }

    /** Writes [text] to the one file this test uses and hands back the ref for it. */
    private fun write(text: String): DocumentRef {
        file().writeText(text)
        return DocumentRef(file().toString())
    }

    /** Opens it the way [rememberOpenDocument] does, but without waiting on a composition. */
    private fun open(text: String): OpenDocument {
        val ref = write(text)
        return runBlocking {
            val contents = store.read(ref)
            OpenDocument(
                store = store,
                editor = EditorState(DocumentSession(contents.text)),
                opened = DocumentSessionState.opened(ref, contents),
            )
        }
    }

    /** Types into the first block, through the field the reader would be typing into. */
    private fun SkikoComposeUiTest.type(
        document: OpenDocument,
        text: String,
    ) {
        document.editor.place(
            Caret(
                document.editor.blocks
                    .first()
                    .id,
                0,
            ),
        )
        waitForIdle()
        onNodeWithText(FIRST_LINE).performTextInput(text)
        waitForIdle()
    }

    /** Ctrl+S, at the root, where the composition root's handler is listening. */
    private fun SkikoComposeUiTest.save() {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
    }

    private fun SkikoComposeUiTest.conflicted(): Boolean =
        onAllNodesWithContentDescription(badge(Strings.STATE_CONFLICTED)).fetchSemanticsNodes().isNotEmpty()

    private fun file(): Path = directory.resolve("note.md")

    private fun badge(label: String) = "${Strings.DOCUMENT_STATE}, $label"

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L

        const val ORIGINAL = "As opened.\n"
        const val FIRST_LINE = "As opened."
        const val THEIRS = "Someone else's edit.\n"
    }
}
