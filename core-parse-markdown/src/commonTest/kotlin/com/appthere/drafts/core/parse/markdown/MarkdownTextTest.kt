package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Backslash escapes and character references.
 *
 * These stopped being the parser's business and became ours: `intellij-markdown` resolves them when
 * generating HTML, and this project never calls its HTML generator. The consequence is the one
 * worth remembering -- [com.appthere.drafts.core.model.Text] now holds semantic text, not a slice
 * of the source.
 */
class MarkdownTextTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `an escaped punctuation character loses its backslash`() {
        assertEquals("*not emphasis*", parser.parse("""\*not emphasis\*""").text())
    }

    @Test
    fun `a backslash before a non-punctuation character is literal`() {
        // CommonMark only lets ASCII punctuation be escaped. `\a` is a backslash and an a.
        assertEquals("""\a""", parser.parse("""\a""").text())
    }

    @Test
    fun `an escaped backslash is one backslash`() {
        assertEquals("""\""", parser.parse("""\\""").text())
    }

    @Test
    fun `named character references resolve`() {
        assertEquals("& < > \" ©", parser.parse("&amp; &lt; &gt; &quot; &copy;").text())
    }

    @Test
    fun `numeric character references resolve in decimal and hex`() {
        assertEquals("# # #", parser.parse("&#35; &#x23; &#X23;").text())
    }

    @Test
    fun `an astral plane reference becomes a surrogate pair`() {
        // Two UTF-16 code units, which is the unit every offset in this project counts in.
        val text = parser.parse("&#x1F4A1;").text()

        assertEquals(2, text.length, "Expected a surrogate pair, got '$text'")
        assertEquals("💡", text)
    }

    @Test
    fun `an unknown reference is left as written`() {
        // A browser does the same, and inventing a character would be worse than passing it on.
        assertEquals("&notAnEntity;", parser.parse("&notAnEntity;").text())
    }

    @Test
    fun `a bare ampersand is not a reference`() {
        assertEquals("a & b", parser.parse("a & b").text())
    }

    @Test
    fun `code spans are literal`() {
        // `\!` inside code is a backslash and a bang. Resolving it would corrupt sample code, which
        // is most of what code spans contain.
        val span =
            parser
                .parse("""`\!` and `&amp;`""")
                .blocks
                .flatMap { (it as Paragraph).inlines }
                .filterIsInstance<CodeSpan>()

        assertEquals(listOf("""\!""", "&amp;"), span.map { it.text })
    }

    @Test
    fun `code blocks are literal`() {
        val code = parser.parse("```\n\\! and &amp;\n```\n").blocks.single() as CodeBlock

        assertEquals("\\! and &amp;", code.text.trimEnd('\n'))
    }

    @Test
    fun `escapes inside a link destination resolve`() {
        val document = parser.parse("""[text](/a\(b\))""")

        assertEquals("/a(b)", document.firstInline<com.appthere.drafts.core.model.Link>().href)
    }

    private fun com.appthere.drafts.core.model.Document.text(): String =
        blocks.filterIsInstance<Paragraph>().joinToString("") { it.plainText() }
}
