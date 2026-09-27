package com.appthere.drafts.editor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The substitution that lets reveal state reflow the author's line breaks.
 *
 * The property the whole approach rests on is that it is one character for one character. If the
 * lengths ever diverge, every offset downstream is wrong -- the caret, the selection, the edit
 * spans handed to the engine -- and the identity mapping becomes a lie rather than a shortcut.
 */
class ReflowNewlinesTest {
    @Test
    fun `text without newlines is passed through untouched`() {
        val text = AnnotatedString("No breaks here.")

        val transformed = reflow.filter(text)

        assertEquals(text, transformed.text)
        assertEquals(OffsetMapping.Identity, transformed.offsetMapping)
    }

    @Test
    fun `a newline becomes a mark rather than a break`() {
        val transformed = reflow.filter(AnnotatedString("one\ntwo"))

        assertEquals("one↵two", transformed.text.text)
        assertTrue('\n' !in transformed.text.text, "A newline survived and will still break the line")
    }

    @Test
    fun `the length never changes`() {
        // The property the identity mapping depends on. Checked over a spread of shapes rather
        // than one, because the failure would be silent: offsets drift and nothing throws.
        val cases =
            listOf(
                "",
                "\n",
                "\n\n\n",
                "a\nb",
                "trailing\n",
                "\nleading",
                "an emoji 😀 and\na break",
            )

        cases.forEach { source ->
            val transformed = reflow.filter(AnnotatedString(source))

            assertEquals(source.length, transformed.text.length, "Length changed for \"$source\"")
        }
    }

    @Test
    fun `every offset maps to itself`() {
        val source = "one\ntwo\nthree"
        val transformed = reflow.filter(AnnotatedString(source))

        source.indices.forEach { offset ->
            assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
            assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
        }
    }

    @Test
    fun `the mark is styled so it reads as a mark and not as text`() {
        val transformed = reflow.filter(AnnotatedString("one\ntwo"))

        val styled = transformed.text.spanStyles.filter { it.item.color == MARKER }
        assertEquals(1, styled.size, "The break mark is not dimmed")
        assertEquals(3, styled.first().start)
        assertEquals(4, styled.first().end)
    }

    @Test
    fun `markup characters are all still shown`() {
        // 4.1 is about markup, and reveal still owes the author every character of it. Only the
        // newline is treated differently, because a newline is whitespace and not markup.
        val source = "A *word*, `code`, and a [link](x).\nSecond line."

        val shown = reflow.filter(AnnotatedString(source)).text.text

        assertEquals(source.replace('\n', '↵'), shown)
    }

    private companion object {
        val MARKER = Color(0xFF5C5C5C)
        val reflow = ReflowNewlines(MARKER)
    }
}
