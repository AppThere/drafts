package com.appthere.drafts.editor.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The caret stays where the reader can see it, and keeps the keyboard with it.
 *
 * A block's field exists only while the list has the block composed, and it is the field that holds
 * keyboard focus. So a caret moved to a block below the window -- by pressing Enter on the last line
 * on screen, which is what writing does -- had no field to go to: focus fell out of the editor and
 * the next keys went elsewhere. On Android, "elsewhere" was the save badge, the first thing on screen
 * that can take focus, and the Enter after that opened *Save As*; the keys after that named a file
 * and the next Enter saved it. Found on a Chromebook, 2026-10-03.
 */
@OptIn(ExperimentalTestApi::class)
class CaretInViewTest {
    @Test
    fun `enter at the bottom of the window keeps typing in the document`() =
        runSkikoComposeUiTest(size = SIZE) {
            val (state, scroll) = show()
            val lastOnScreen =
                scroll.layoutInfo.visibleItemsInfo
                    .last()
                    .index
            state.place(Caret(state.blocks[lastOnScreen].id, state.sourceOf(state.blocks[lastOnScreen].block).length))
            waitForIdle()

            // Well past the bottom edge, the way the Chromebook run went: a line, then Enter for a
            // new paragraph below it, over and over.
            repeat(PAST_THE_EDGE) { line ->
                onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
                waitForIdle()
                onNode(isFocused()).performTextInput("Line $line")
                waitForIdle()
            }
            onNode(isFocused()).performTextInput(TYPED)
            waitForIdle()

            val holding = state.blocks.indexOfFirst { TYPED in state.sourceOf(it.block) }
            assertTrue(holding >= 0, "What was typed after the Enters did not reach the document")
            assertOnScreen(scroll, holding)
        }

    @Test
    fun `a caret placed far below the window brings its block into view with focus`() =
        runSkikoComposeUiTest(size = SIZE) {
            val (state, scroll) = show()
            val far = state.blocks.size - 2

            state.place(Caret(state.blocks[far].id, 0))
            waitForIdle()

            assertOnScreen(scroll, far)
            onNode(isFocused()).performTextInput(TYPED)
            waitForIdle()
            assertTrue(TYPED in state.sourceOf(state.blocks[far].block), "The keys did not go to the caret's block")
        }

    private fun SkikoComposeUiTest.show(): Pair<EditorState, LazyListState> {
        val state =
            EditorState(
                DocumentSession(List(PARAGRAPHS) { "Paragraph $it, long enough to be a line." }.joinToString("\n\n")),
            )
        val scroll = LazyListState()
        setContent {
            // The window's own focus target, as the application's root provides one: the place focus
            // falls back to when it leaves the editor, which is what this guards against.
            val root = remember { FocusRequester() }
            DraftsTheme {
                Box(Modifier.fillMaxSize().focusRequester(root).focusable()) {
                    LaunchedEffect(Unit) { root.requestFocus() }
                    BlockEditor(state = state, scroll = scroll)
                }
            }
        }
        waitForIdle()
        return state to scroll
    }

    private fun assertOnScreen(
        scroll: LazyListState,
        index: Int,
    ) {
        val shown = scroll.layoutInfo.visibleItemsInfo.map { it.index }
        assertTrue(index in shown, "Block $index is off screen; the window shows $shown")
    }

    private companion object {
        val SIZE = Size(700f, 500f)
        const val PARAGRAPHS = 40
        const val PAST_THE_EDGE = 20
        const val TYPED = "typed here"
    }
}
