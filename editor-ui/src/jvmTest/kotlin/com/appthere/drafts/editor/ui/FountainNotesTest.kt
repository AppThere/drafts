package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.Screenplay
import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 4.5's table, for the parts of a screenplay that are about it rather than in it:
 *
 * | Element | Preview | Reveal |
 * |---|---|---|
 * | Notes `[[ ]]`, Boneyard `/* */` | Dimmed, collapsible | Full source |
 * | Sections `#`, Synopses `=` | Dimmed, outline-only styling | Full source |
 */
@OptIn(ExperimentalTestApi::class)
class FountainNotesTest {
    @Test
    fun `a note is dimmed and shown in full by default`() {
        val preview = previewOf(LINE_WITH_NOTE, collapse = false)

        assertEquals(LINE_WITH_NOTE, preview.text.text)
        assertEquals(MUTED, colourAt(preview.text, LINE_WITH_NOTE.indexOf(NOTE)))
        assertEquals(Color.Unspecified, colourAt(preview.text, 0), "The words around the note were dimmed")
    }

    @Test
    fun `a collapsed note is its own brackets around an ellipsis`() {
        val preview = previewOf(LINE_WITH_NOTE, collapse = true)

        assertEquals("She waits. [[…]] Nothing.", preview.text.text)
        assertEquals("She waits. Nothing.", preview.spoken.replace("  ", " "))
    }

    @Test
    fun `a collapsed boneyard is one line in its own delimiters`() {
        val preview = previewOf("Before.\n\n$BONEYARD\n\nAfter.\n", collapse = true, block = 1)

        assertEquals("/* … */", preview.text.text)
    }

    @Test
    fun `sections and synopses are dimmed and a synopsis is set in italic`() {
        val document = "# ACT ONE\n\n= She learns the truth.\n\nINT. HOUSE - DAY\n"

        assertEquals(MUTED, colourAt(previewOf(document, collapse = false, block = 0).text, 0))
        assertEquals(MUTED, colourAt(previewOf(document, collapse = false, block = 1).text, 0))
        assertEquals(Color.Unspecified, colourAt(previewOf(document, collapse = false, block = 2).text, 0))
        assertTrue(Screenplay.Synopsis.italic)
    }

    @Test
    fun `collapsing makes the boneyard take one line of the page`() =
        runSkikoComposeUiTest(size = SIZE) {
            // Five lines of source. Reserving the taller state, as every other row does (4.2), would
            // keep all five and show one: one line and the blank lines either side is under four.
            show("Before.\n\n$BONEYARD\n\nAfter.\n", collapse = true)

            val line = heightOf("Before.")
            val gap = topOf("After.") - onNodeWithText("Before.").getBoundsInRoot().bottom
            assertTrue(gap < line * 4, "Collapsing kept the room the boneyard took: $gap for a line of $line")
        }

    @Test
    fun `a collapsed note is shown in full once the caret is in it`() =
        runSkikoComposeUiTest(size = SIZE) {
            val state = show("$LINE_WITH_NOTE\n\nAfter.\n", collapse = true)

            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            val field = onNode(isFocused()).fetchSemanticsNode().config
            assertEquals(LINE_WITH_NOTE, field[SemanticsProperties.EditableText].text)
        }

    @Test
    fun `a screen reader hears what was collapsed`() =
        runSkikoComposeUiTest(size = SIZE) {
            show("Before.\n\n$BONEYARD\n\n$LINE_WITH_NOTE\n", collapse = true)

            onNodeWithContentDescription("Boneyard").assertExists()
            onNodeWithContentDescription("Notes collapsed, She waits. Nothing.", substring = true).assertExists()
        }

    @Test
    fun `prose is untouched by the setting`() {
        val document = "Some <b>raw</b> HTML.\n"
        val block = DocumentSession(document).blocks.first().block

        assertEquals(previewOfBlock(block, MUTED).text, previewOfBlock(block, MUTED, collapseNotes = true).text)
        assertTrue(!holdsNotes(block))
    }

    private fun previewOf(
        text: String,
        collapse: Boolean,
        block: Int = 0,
    ): BlockPreview = previewOfBlock(DocumentSession(text, BlockParser.Fountain()).blocks[block].block, MUTED, collapse)

    private fun colourAt(
        text: AnnotatedString,
        at: Int,
    ): Color =
        text.spanStyles
            .filter { at >= it.start && at < it.end }
            .map { it.item.color }
            .lastOrNull { it != Color.Unspecified } ?: Color.Unspecified

    private fun SkikoComposeUiTest.show(
        text: String,
        collapse: Boolean,
    ): EditorState {
        val state = EditorState(DocumentSession(text, BlockParser.Fountain()))
        setContent { DraftsTheme(ReaderSettings(collapseNotes = collapse)) { BlockEditor(state = state) } }
        waitForIdle()
        return state
    }

    private fun SkikoComposeUiTest.topOf(text: String): Dp = onNodeWithText(text).getBoundsInRoot().top

    private fun SkikoComposeUiTest.heightOf(text: String): Dp =
        onNodeWithText(text).getBoundsInRoot().let { it.bottom - it.top }

    private companion object {
        val SIZE = Size(1200f, 900f)
        val MUTED = Color(0xFF777777)

        const val NOTE = "[[Make this longer]]"
        const val LINE_WITH_NOTE = "She waits. $NOTE Nothing."
        const val BONEYARD = "/*\nINT. OLD SCENE - DAY\n\nThis whole scene is cut for now.\n*/"
    }
}
