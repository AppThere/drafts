package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.model.Underline
import com.appthere.drafts.core.model.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `fountain.md`'s emphasis table, its escapes, and the two things emphasis does not apply inside.
 *
 * | `*text*` | Italic | `**text**` | Bold | `***text***` | Bold italic | `_text_` | Underline |
 */
class InlinesTest {
    @Test
    fun `single asterisks are italic`() {
        val italic = assertIs<Emphasis>(parse("an *italic* word").single { it is Emphasis })

        assertEquals(false, italic.strong)
        assertEquals("italic", italic.children.plainText())
    }

    @Test
    fun `double asterisks are bold`() {
        val bold = assertIs<Emphasis>(parse("a **bold** word").single { it is Emphasis })

        assertEquals(true, bold.strong)
        assertEquals("bold", bold.children.plainText())
    }

    @Test
    fun `triple asterisks are bold around italic`() {
        val outer = assertIs<Emphasis>(parse("***both***").single())
        val inner = assertIs<Emphasis>(outer.children.single())

        assertEquals(true, outer.strong)
        assertEquals(false, inner.strong)
        assertEquals("both", inner.children.plainText())
    }

    @Test
    fun `an underscore underlines rather than italicises`() {
        // The one place Fountain and Markdown read the same five characters differently.
        val underline = assertIs<Underline>(parse("_underlined_").single())

        assertEquals("underlined", underline.children.plainText())
    }

    @Test
    fun `emphasis nests`() {
        // The spec's own example: "_an *italicized* word within an underlined phrase_".
        val underline = assertIs<Underline>(parse("_an *italicized* word_").single())

        assertTrue(underline.children.any { it is Emphasis }, "The italic inside was not found")
        assertEquals("an italicized word", underline.children.plainText())
    }

    @Test
    fun `markers surrounded by spaces are literal`() {
        // "Asterisks or underscores surrounded by spaces on both sides are treated as literal
        // characters -- `a * b` is literal."
        assertEquals("a * b", parse("a * b").plainText())
        assertTrue(parse("a * b").none { it is Emphasis })
        assertEquals("x _ y", parse("x _ y").plainText())

        // Two of them, which is the case that tells the rule apart from "there was no closer":
        // arithmetic in a stage direction must not emphasise the number between the operators.
        val arithmetic = parse("2 * 3 * 4 grams")
        assertTrue(arithmetic.none { it is Emphasis }, "A multiplication became emphasis")
        assertEquals("2 * 3 * 4 grams", arithmetic.plainText())

        // And the asymmetric case, which is what the opening half of the rule is for: a marker
        // with a space after it does not open, even though a later one could have closed it.
        val lopsided = parse("a * text* b")
        assertTrue(lopsided.none { it is Emphasis }, "A space-flanked marker opened emphasis")
        assertEquals("a * text* b", lopsided.plainText())
    }

    @Test
    fun `a marker with a space before it does not close`() {
        // The other half of the rule. `*text *` has an opener that could have opened, and a marker
        // that cannot close, so neither is markup and both stay in the words.
        val inlines = parse("*text *")

        assertTrue(inlines.none { it is Emphasis }, "A space-preceded marker closed emphasis")
        assertEquals("*text *", inlines.plainText())
    }

    @Test
    fun `a backslash escapes a marker`() {
        val inlines = parse("literally \\*not emphasis\\*")

        assertTrue(inlines.none { it is Emphasis }, "The escaped asterisks opened emphasis")
        assertEquals("literally *not emphasis*", inlines.plainText())
    }

    @Test
    fun `an unclosed marker is literal`() {
        assertEquals("a *lonely asterisk", parse("a *lonely asterisk").plainText())
    }

    @Test
    fun `a note is opaque and says nothing`() {
        // "Inline, delimited by double square brackets. Not rendered."
        val inlines = parse("She crosses. [[check this beat]] She stops.")
        val note = assertIs<RawInline>(inlines.single { it is RawInline })

        assertEquals(Origin.FOUNTAIN_NOTE, note.origin)
        assertEquals("[[check this beat]]", note.text)
        assertEquals("She crosses.  She stops.", inlines.plainText())
    }

    @Test
    fun `emphasis does not apply inside a note`() {
        val note = assertIs<RawInline>(parse("[[an *emphatic* note]]").single())

        assertEquals("[[an *emphatic* note]]", note.text)
    }

    @Test
    fun `a boneyard opened mid-line is opaque too`() {
        val inlines = parse("She crosses. /" + "* cut *" + "/ She stops.")
        val cut = assertIs<RawInline>(inlines.single { it is RawInline })

        assertEquals(Origin.FOUNTAIN_BONEYARD, cut.origin)
        assertEquals("She crosses.  She stops.", inlines.plainText())
    }

    @Test
    fun `an unterminated note stays in the words`() {
        // A writer who has typed the opening bracket and not the thought yet.
        assertEquals("She crosses. [[check", parse("She crosses. [[check").plainText())
    }

    @Test
    fun `every inline's span is the source it came from`() {
        val text = "an *italic* and _an underline_ and [[a note]]"
        val inlines = parse(text)

        inlines.forEach { inline ->
            val span = requireNotNull(inline.source) { "An inline with no source: $inline" }
            assertTrue(span.start.value < span.endExclusive.value, "An empty span: $span")
        }

        assertEquals(text, inlines.joinToString("") { spanOf(text, it) })
    }

    @Test
    fun `prose with no markup is one piece of text`() {
        val only = assertIs<Text>(parse("She crosses the room.").single())

        assertEquals("She crosses the room.", only.value)
    }

    private fun parse(text: String): List<Inline> = inlinesIn(text, SourceSpan.of(0, text.length))

    private fun spanOf(
        text: String,
        inline: Inline,
    ): String = inline.source!!.let { text.substring(it.start.value, it.endExclusive.value) }
}
