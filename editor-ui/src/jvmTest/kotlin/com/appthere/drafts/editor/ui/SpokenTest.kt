package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 10.1, as the semantics tree carries it to a screen reader.
 *
 * What the tree says is what TalkBack, VoiceOver, NVDA and Orca are given; whether each speaks it
 * well is the accessibility audit's to find out, with people who use them daily.
 */
@OptIn(ExperimentalTestApi::class)
class SpokenTest {
    @Test
    fun `a quote is named before its words`() =
        runSkikoComposeUiTest(size = SIZE) {
            show("> $QUOTE\n\n$PARAGRAPH\n")

            onNodeWithContentDescription("Block quote, $QUOTE").assertExists()
        }

    @Test
    fun `decoration the preview draws is not read out`() =
        runSkikoComposeUiTest(size = SIZE) {
            // The bullets are the preview's, not the document's. Read aloud, every item would begin
            // with "bullet".
            show("- One\n- Two\n\n$PARAGRAPH\n")

            onNodeWithContentDescription("Bulleted list, One Two").assertExists()
        }

    @Test
    fun `a code block is read as its code, not its fences`() =
        runSkikoComposeUiTest(size = SIZE) {
            // The fences show in preview on purpose, and are source like the code. Read aloud they
            // are "grave accent" three times before every block.
            show("```kotlin\nfun main() {}\n```\n\n$PARAGRAPH\n")

            onNodeWithContentDescription("Code block, Kotlin, fun main() {}").assertExists()
        }

    @Test
    fun `a heading is a heading`() =
        runSkikoComposeUiTest(size = SIZE) {
            // What lets a screen reader move from heading to heading.
            show("## $TITLE\n\n$PARAGRAPH\n")

            onNodeWithText(TITLE).assert(isHeading())
        }

    @Test
    fun `a paragraph is read as itself`() =
        runSkikoComposeUiTest(size = SIZE) {
            show("$PARAGRAPH\n")

            onNodeWithText(PARAGRAPH).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }

    @Test
    fun `the block being edited is read as its source`() =
        runSkikoComposeUiTest(size = SIZE) {
            // 10.1: "Never let the announced text and the editable text disagree." The field holds
            // "> Salt ...", markup and all, and is read as exactly that -- not as a described quote.
            val state = show("> $QUOTE\n\n$PARAGRAPH\n")

            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            onNode(hasEditableText("> $QUOTE")).assertExists()
            assertTrue(onAllNodesWithContentDescription("Block quote, $QUOTE").fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `a paragraph promoted to a heading is announced`() =
        runSkikoComposeUiTest(size = SIZE) {
            val state = show("$PARAGRAPH\n\n$SECOND\n")
            val block = state.blocks.first()
            state.place(Caret(block.id, 0))
            waitForIdle()

            state.replace(requireNotNull(block.block.source), "## $PARAGRAPH", 3)
            waitForIdle()

            onNodeWithContentDescription("Heading level 2.").assertExists()
        }

    @Test
    fun `moving the caret is not announced`() =
        runSkikoComposeUiTest(size = SIZE) {
            // Reveal and preview are "visual affordances, not content changes".
            val state = show("## $TITLE\n\n$PARAGRAPH\n")

            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()
            state.place(Caret(state.blocks.last().id, 0))
            waitForIdle()

            assertTrue(onAllNodesWithContentDescription("Heading level 2.").fetchSemanticsNodes().isEmpty())
            assertTrue(onAllNodesWithContentDescription("Paragraph.").fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `two blocks merged are announced`() =
        runSkikoComposeUiTest(size = SIZE) {
            val state = show("$PARAGRAPH\n\n$SECOND\n")
            state.place(Caret(state.blocks.last().id, 0))
            waitForIdle()

            state.mergeWithPrevious()
            waitForIdle()

            onNodeWithContentDescription("Joined with the block above.").assertExists()
        }

    private fun SkikoComposeUiTest.show(text: String): EditorState {
        val state = EditorState(DocumentSession(text))
        setContent { BlockEditor(state = state) }
        waitForIdle()
        return state
    }

    private fun hasEditableText(text: String) =
        SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText,
            androidx.compose.ui.text
                .AnnotatedString(text),
        )

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val QUOTE = "Salt gets into everything."
        const val TITLE = "The Salt Road"
        const val PARAGRAPH = "The road ran along the shore."
        const val SECOND = "It was not a road for carts."
    }
}
