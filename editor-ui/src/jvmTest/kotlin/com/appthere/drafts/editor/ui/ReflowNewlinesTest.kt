package com.appthere.drafts.editor.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The substitution that lets reveal state reflow the author's line breaks.
 *
 * The property the whole approach rests on is that it is one character for one character. If the
 * lengths ever diverge, every offset downstream is wrong -- the caret, the selection, the edit
 * spans handed to the engine -- and the identity mapping becomes a lie rather than a shortcut.
 *
 * Read from what the field actually lays out, which is the only place an output transformation's
 * result can be seen from outside it.
 */
@OptIn(ExperimentalTestApi::class)
class ReflowNewlinesTest {
    @Test
    fun `text without newlines is passed through untouched`() {
        assertEquals("No breaks here.", shown("No breaks here.").text)
    }

    @Test
    fun `a newline becomes a mark rather than a break`() {
        val text = shown("one\ntwo").text

        assertEquals("one↵two", text)
        assertTrue('\n' !in text, "A newline survived and will still break the line")
    }

    @Test
    fun `the length never changes`() {
        // The property the identity mapping depends on. Checked over a spread of shapes rather
        // than one, because the failure would be silent: offsets drift and nothing throws.
        listOf("", "\n", "\n\n\n", "a\nb", "trailing\n", "\nleading", "an emoji 😀 and\na break").forEach { source ->
            assertEquals(source.length, shown(source).length, "Length changed for \"$source\"")
        }
    }

    @Test
    fun `the mark is styled so it reads as a mark and not as text`() {
        val styled = shown("one\ntwo").spanStyles.filter { it.item.color == MARKER }

        assertEquals(1, styled.size, "The break mark is not dimmed")
        assertEquals(3, styled.first().start)
        assertEquals(4, styled.first().end)
    }

    @Test
    fun `markup characters are all still shown`() {
        // 4.1 is about markup, and reveal still owes the author every character of it. Only the
        // newline is treated differently, because a newline is whitespace and not markup.
        val source = "A *word*, `code`, and a [link](x).\nSecond line."

        assertEquals(source.replace('\n', '↵'), shown(source).text)
    }

    /** What a field showing [source] through the transformation lays out. */
    private fun shown(source: String): AnnotatedString {
        var laidOut: AnnotatedString? = null
        runSkikoComposeUiTest(size = Size(600f, 200f)) {
            setContent {
                BasicTextField(
                    state = TextFieldState(source),
                    outputTransformation = ReflowNewlines(MARKER),
                    onTextLayout = { result -> laidOut = result()?.layoutInput?.text },
                )
            }
            waitForIdle()
        }
        return laidOut ?: error("The field never laid out")
    }

    private companion object {
        val MARKER = Color(0xFF5C5C5C)
    }
}
