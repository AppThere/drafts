package com.appthere.drafts.editor.ui

import androidx.compose.ui.graphics.Color
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which blocks may have their newlines reflowed, and which may not.
 *
 * The rule is CommonMark's, not a preference: a bare newline is a *soft* break -- whitespace the
 * parser turns into a space -- inside a paragraph, and structure everywhere else. Getting this
 * wrong is not subtle but it is quiet: the first version reflowed everything, and a four-line code
 * block drew as a single line while reserving the other three.
 */
class SoftWrapTest {
    @Test
    fun `a paragraph is soft-wrapped`() {
        assertTrue(softWrapped("First line\nsecond line."))
    }

    @Test
    fun `a fenced code block is not`() {
        assertFalse(softWrapped("```kotlin\nfun main() {\n}\n```"), "Code lines are the code's own")
    }

    @Test
    fun `an indented code block is not`() {
        assertFalse(softWrapped("    fun main() {\n    }"))
    }

    @Test
    fun `a list is not`() {
        assertFalse(softWrapped("- first item\n- second item"), "Newlines separate the items")
    }

    @Test
    fun `a quote is not`() {
        assertFalse(softWrapped("> first line\n> second line"))
    }

    @Test
    fun `a table is not`() {
        assertFalse(softWrapped("| a | b |\n| - | - |\n| 1 | 2 |"), "Newlines separate the rows")
    }

    @Test
    fun `a setext heading is not`() {
        // Its second line is the underline that makes it a heading -- markup, not wrapping.
        assertFalse(softWrapped("A heading\n========="))
    }

    private fun softWrapped(source: String): Boolean {
        val state = EditorState(DocumentSession("$source\n"))
        return state.rowContentOf(state.blocks.first(), Color.Unspecified).softWrapped
    }
}
