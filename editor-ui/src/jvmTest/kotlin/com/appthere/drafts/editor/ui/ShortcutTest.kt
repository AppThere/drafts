package com.appthere.drafts.editor.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 10.2's shortcut map: a shortcut is exactly its keys.
 *
 * Exact because the tables hold pairs that differ only by a modifier -- Ctrl+Z and Ctrl+Shift+Z,
 * Ctrl+S and Ctrl+Shift+S -- and a match that ignored the extra key would undo what the reader
 * asked to redo.
 */
@OptIn(ExperimentalTestApi::class)
class ShortcutTest {
    private val undo = EditorShortcuts.Undo
    private val redo = EditorShortcuts.Redo
    private val delete = EditorShortcuts.DeleteBackward

    @Test
    fun `ctrl and the key is the shortcut`() {
        assertEquals(listOf(undo), matched { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } })
    }

    @Test
    fun `command stands for ctrl`() {
        // Every handler has always accepted either, and the list says so once.
        assertEquals(listOf(undo), matched { withKeyDown(Key.MetaLeft) { pressKey(Key.Z) } })
    }

    @Test
    fun `shift makes it a different shortcut rather than the same one`() {
        val pressed = matched { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Z) } } }

        assertEquals(listOf(redo), pressed)
    }

    @Test
    fun `the key alone is not the shortcut`() {
        // Typing a z is typing, not undoing.
        assertEquals(emptyList(), matched { pressKey(Key.Z) })
    }

    @Test
    fun `a shortcut without a modifier is not taken with one`() {
        // Ctrl+Backspace deletes a word in a field; it is not "delete the selection".
        assertEquals(emptyList(), matched { withKeyDown(Key.CtrlLeft) { pressKey(Key.Backspace) } })
        assertEquals(listOf(delete), matched { pressKey(Key.Backspace) })
    }

    @Test
    fun `the keys are named the way the list shows them`() {
        // Through a composition, because 11.1 put the key names in resources and a resource is read
        // from one. What is asserted is still the notation: the modifiers in order, joined by a
        // plus, ending in the key.
        runSkikoComposeUiTest(size = Size(SIDE, SIDE)) {
            val named = mutableListOf<String>()
            setContent { named += listOf(undo.keys(), redo.keys(), delete.keys()) }
            waitForIdle()

            assertEquals(listOf("Ctrl+Z", "Ctrl+Shift+Z", "Backspace"), named.take(3))
        }
    }

    /** Which of the undo, redo and delete shortcuts the keys pressed in [keys] matched. */
    private fun matched(keys: KeyInjectionScope.() -> Unit): List<Shortcut> {
        val seen = mutableListOf<Shortcut>()
        runSkikoComposeUiTest(size = Size(SIDE, SIDE)) {
            setContent {
                val focus = remember { FocusRequester() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            seen += listOf(undo, redo, delete).filter { it.matches(event) }
                            false
                        },
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            waitForIdle()

            onRoot().performKeyInput(keys)
        }
        return seen
    }

    private companion object {
        const val SIDE = 200f
    }
}
