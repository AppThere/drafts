package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertTrue

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
            // Wait for the badge, not for the file. The bytes land on an IO thread and
            // `savedRevision` is only set when the coroutine resumes afterwards, so the file can
            // be written while the badge still says `Unsaved` -- which passed on this machine and
            // failed on CI, where the resume is slower.
            waitUntil(timeoutMillis = TIMEOUT) { showing(badge(Strings.STATE_CLEAN)) }

            assertEquals("Mine. $ORIGINAL", file().readText())
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

    @Test
    fun `the refusal is put to the reader as a dialog`() {
        // 8.4: "Dialogs only on attempted write." This is that write.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            file().writeText(THEIRS)

            save()
            waitUntil(timeoutMillis = TIMEOUT) { asking() }

            onNodeWithText(Strings.CONFLICT, useUnmergedTree = true).assertExists()
            onNodeWithContentDescription(Strings.RELOAD).assertExists()
            onNodeWithContentDescription(Strings.CANCEL).assertExists()
        }
    }

    @Test
    fun `a save that succeeds asks the reader nothing`() {
        // The other half of "only on attempted write": most writes are not refused, and a dialog on
        // every Ctrl+S would be the interruption 8.4 is written to avoid.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")

            save()
            // Waiting for the badge rather than the file, so the save has demonstrably finished
            // before anything asserts a dialog is absent. Waiting for the bytes alone would let
            // this pass while the outcome was still on its way back.
            waitUntil(timeoutMillis = TIMEOUT) { showing(badge(Strings.STATE_CLEAN)) }

            onNodeWithText(Strings.CONFLICT, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `cancelling dismisses the dialog and changes nothing`() {
        // Cancel is the choice that must be safe: the edits stay unsaved, the other version stays
        // on disk, and the badge goes on saying so quietly.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            file().writeText(THEIRS)
            save()
            waitUntil(timeoutMillis = TIMEOUT) { asking() }

            onNodeWithContentDescription(Strings.CANCEL).performClick()
            waitForIdle()

            onNodeWithText(Strings.CONFLICT, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithContentDescription(badge(Strings.STATE_CONFLICTED)).assertExists()
            assertEquals(THEIRS, file().readText())
            assertEquals("Mine. $ORIGINAL", document.editor.text)
        }
    }

    @Test
    fun `the dialog stays dismissed until the reader tries to save again`() {
        // The reason the dialog is driven by the refusal and not by the state. The document is still
        // conflicted after cancelling, so anything keyed off `conflicted` would put the dialog
        // straight back and leave the reader unable to get on with anything.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            file().writeText(THEIRS)
            save()
            waitUntil(timeoutMillis = TIMEOUT) { asking() }
            onNodeWithContentDescription(Strings.CANCEL).performClick()
            waitForIdle()

            type(document, "More. ")

            onNodeWithText(Strings.CONFLICT, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `reloading replaces the document with what is on disk`() {
        // 8.2's "[ Reload and lose my changes ]", end to end.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            file().writeText(THEIRS)
            save()
            waitUntil(timeoutMillis = TIMEOUT) { asking() }

            onNodeWithContentDescription(Strings.RELOAD).performClick()
            waitUntil(timeoutMillis = TIMEOUT) { document.editor.text == THEIRS }

            onNodeWithText(Strings.CONFLICT, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithContentDescription(badge(Strings.STATE_CLEAN)).assertExists()
            onNodeWithText(THEIR_LINE).assertExists()
        }
    }

    @Test
    fun `the dialog can be answered without a pointer`() {
        // 10.2: "Complete keyboard operation. Every action reachable without pointer." A dialog is
        // the sharpest case -- it is asking a question, and a reader who cannot answer it cannot
        // get back to their document at all.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")
            file().writeText(THEIRS)
            save()
            waitUntil(timeoutMillis = TIMEOUT) { asking() }

            onRoot().performKeyInput { pressKey(Key.Escape) }
            waitForIdle()

            onNodeWithText(Strings.CONFLICT, useUnmergedTree = true).assertDoesNotExist()
            assertEquals(THEIRS, file().readText(), "Escape wrote something")
        }
    }

    @Test
    fun `the document can be saved without a keyboard`() {
        // A phone has no Ctrl. Until now saving was reachable only by shortcut, so on a touch
        // device there was no way to save at all -- 10.2's "every action reachable without pointer"
        // read the other way round.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")

            onNodeWithContentDescription(badge(Strings.STATE_DIRTY)).performClick()
            waitUntil(timeoutMillis = TIMEOUT) { showing(badge(Strings.STATE_CLEAN)) }

            assertEquals("Mine. $ORIGINAL", file().readText())
        }
    }

    @Test
    fun `a screen reader can save the document`() {
        // The audience this control exists for most. A touch reader navigates by the semantics
        // tree and activates through it, not by tapping where a sighted reader would -- and
        // `clearAndSetSemantics` drops the click action along with everything else it clears, so
        // the button can be perfectly tappable and invisible to them at the same time.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")

            onNodeWithContentDescription(badge(Strings.STATE_DIRTY))
                .performSemanticsAction(SemanticsActions.OnClick)
            waitUntil(timeoutMillis = TIMEOUT) { showing(badge(Strings.STATE_CLEAN)) }

            assertEquals("Mine. $ORIGINAL", file().readText())
        }
    }

    @Test
    fun `a saved document offers nothing to press`() {
        // A control that does nothing is worse than no control. With everything already in the
        // file there is nothing to save, so the indicator is a status and not a button.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            waitForIdle()

            onNodeWithContentDescription(badge(Strings.STATE_CLEAN)).assertHasNoClickAction()
        }
    }

    @Test
    fun `the save target is big enough to hit`() {
        // 10.2: "Touch targets >= 48dp." The indicator is a dot four across; as a button it has to
        // be something a thumb can find.
        runSkikoComposeUiTest(size = SIZE) {
            val document = open(ORIGINAL)
            setContent { DraftsApp(document = document) }
            type(document, "Mine. ")

            val bounds = onNodeWithContentDescription(badge(Strings.STATE_DIRTY)).getBoundsInRoot()
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top

            assertTrue(height >= TOUCH_TARGET && width >= TOUCH_TARGET, "The save target is $width by $height")
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

    /**
     * Types into the first block, through the field the reader would be typing into.
     *
     * The field is found by the block's *current* source rather than by a constant. Despite
     * describing itself as "contains", `onNodeWithText` matches exactly, so a second call looking
     * for the original line finds nothing once the first call has changed it.
     */
    private fun SkikoComposeUiTest.type(
        document: OpenDocument,
        text: String,
    ) {
        val block = document.editor.blocks.first()
        document.editor.place(Caret(block.id, 0))
        waitForIdle()

        onNodeWithText(document.editor.sourceOf(block.block)).performTextInput(text)
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

    /** True once a node with [description] is on screen. */
    private fun SkikoComposeUiTest.showing(description: String): Boolean =
        onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    /** True once 8.2's dialog is on screen. */
    private fun SkikoComposeUiTest.asking(): Boolean =
        onAllNodesWithContentDescription(Strings.RELOAD).fetchSemanticsNodes().isNotEmpty()

    private fun SkikoComposeUiTest.conflicted(): Boolean =
        onAllNodesWithContentDescription(badge(Strings.STATE_CONFLICTED)).fetchSemanticsNodes().isNotEmpty()

    private fun file(): Path = directory.resolve("note.md")

    private fun badge(label: String) = "${Strings.DOCUMENT_STATE}, $label"

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L

        /** 10.2's minimum. */
        val TOUCH_TARGET = 48.dp

        const val ORIGINAL = "As opened.\n"
        const val FIRST_LINE = "As opened."
        const val THEIRS = "Someone else's edit.\n"
        const val THEIR_LINE = "Someone else's edit."
    }
}
