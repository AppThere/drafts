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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.KeywordPreset
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.apply
import com.appthere.drafts.i18n.resources.scene_headings
import com.appthere.drafts.i18n.resources.scene_headings_language
import com.appthere.drafts.i18n.resources.scene_headings_not_saved
import com.appthere.drafts.i18n.resources.scene_headings_words
import com.appthere.drafts.i18n.resources.transition_ending
import com.appthere.drafts.i18n.resources.transition_ending_needed
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 11.3's scene-heading words, chosen where a reader would look for them: the reader controls of
 * a screenplay, and nowhere for prose.
 */
@OptIn(ExperimentalTestApi::class)
class SceneHeadingsTest {
    @Test
    fun `prose offers no scene headings`() =
        runSkikoComposeUiTest(size = SIZE) {
            show(FakeDocumentStore(PROSE, "A paragraph.\n"), PROSE, screenplay = false)

            assertTrue(onAllNodesWithText(words(Res.string.scene_headings)).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `choosing a language reads the screenplay again and keeps the choice`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(SCRIPT, TEXT)
            val editor = show(store, SCRIPT)
            assertEquals(
                BlockRole.ACTION,
                editor.blocks
                    .first()
                    .block.role,
            )

            openHeadings()
            onNodeWithContentDescription("${words(Res.string.scene_headings_language)}, Français").performClick()
            waitForIdle()

            assertEquals(
                BlockRole.SCENE_HEADING,
                editor.blocks
                    .first()
                    .block.role,
            )
            assertEquals(TEXT, editor.text, "Choosing the words changed the screenplay's text")
            val kept = runBlocking { SettingsStore(store, SETTINGS).keywordsFor(IDENTITY) }
            assertEquals(FRENCH, kept)
        }

    @Test
    fun `typed words apply on apply`() =
        runSkikoComposeUiTest(size = SIZE) {
            val editor = show(FakeDocumentStore(SCRIPT, TEXT), SCRIPT)

            openHeadings()
            onNodeWithContentDescription(
                words(Res.string.scene_headings_words),
            ).performTextReplacement("INT, INTÉRIEUR")
            waitForIdle()
            assertEquals(
                BlockRole.ACTION,
                editor.blocks
                    .first()
                    .block.role,
                "The words applied before Apply",
            )

            onNodeWithContentDescription(words(Res.string.apply)).performClick()
            waitForIdle()

            assertEquals(
                BlockRole.SCENE_HEADING,
                editor.blocks
                    .first()
                    .block.role,
            )
            assertEquals(listOf("INT", "INTÉRIEUR"), editor.keywords?.sceneHeadingPrefixes)
        }

    @Test
    fun `an empty transition ending is refused and said`() =
        runSkikoComposeUiTest(size = SIZE) {
            val editor = show(FakeDocumentStore(SCRIPT, TEXT), SCRIPT)

            openHeadings()
            onNodeWithContentDescription(words(Res.string.transition_ending)).performTextReplacement("")
            onNodeWithContentDescription(words(Res.string.apply)).performClick()
            waitForIdle()

            assertEquals(FountainKeywords.ENGLISH, editor.keywords)
            assertTrue(
                onAllNodesWithText(words(Res.string.transition_ending_needed)).fetchSemanticsNodes().isNotEmpty(),
            )
        }

    @Test
    fun `words that could not be kept say so and still apply`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(SCRIPT, TEXT)
            val editor = show(store, SCRIPT)
            store.failsToWrite = true

            openHeadings()
            onNodeWithContentDescription("${words(Res.string.scene_headings_language)}, Français").performClick()

            waitUntil(timeoutMillis = TIMEOUT) {
                onAllNodesWithText(words(Res.string.scene_headings_not_saved)).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                BlockRole.SCENE_HEADING,
                editor.blocks
                    .first()
                    .block.role,
            )
        }

    /** [ref] open in a window whose words are kept in [store], with its keeper's identity. */
    private fun SkikoComposeUiTest.show(
        store: FakeDocumentStore,
        ref: DocumentRef,
        screenplay: Boolean = true,
    ): EditorState {
        val document =
            runBlocking {
                val contents = store.read(ref)
                val parser = if (screenplay) BlockParser.Fountain() else BlockParser.Markdown()
                OpenDocument(
                    store = store,
                    editor = EditorState(DocumentSession(contents.text, parser)),
                    opened = DocumentSessionState.opened(ref, contents),
                )
            }
        val keeper = SnapshotKeeper(document, SnapshotStore(store, "/sessions"), IDENTITY)

        setContent {
            DraftsApp(document = document, keeper = keeper, settingsStore = SettingsStore(store, SETTINGS))
        }
        waitForIdle()

        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Comma)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        return document.editor
    }

    private fun SkikoComposeUiTest.openHeadings() {
        // The link is at the foot of the controls, which scroll: on a short window it is below the fold.
        onNodeWithContentDescription(words(Res.string.scene_headings)).performScrollTo().performClick()
        waitForIdle()
    }

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L

        val SCRIPT = DocumentRef("/scripts/cuisine.fountain")
        val PROSE = DocumentRef("/documents/note.md")
        const val TEXT = "INTÉRIEUR CUISINE - JOUR\n\nElle entre.\n"
        const val SETTINGS = "/settings"

        val IDENTITY =
            SessionIdentity(
                documentId = "cuisine",
                uri = "file:///scripts/cuisine.fountain",
                displayName = "cuisine.fountain",
                kind = "fountain",
            )
        val FRENCH = KeywordPreset.ALL.single { it.name == "Français" }.keywords
    }
}
