package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.RawInline
import com.appthere.drafts.core.model.RawPassthrough
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hugo shortcodes (`markdown-dialect.md`, "Handled outside the Markdown parser").
 *
 * "Tokenised into opaque atomic spans before parsing, restored verbatim on serialise. Never parsed,
 * never reformatted."
 */
class ShortcodeTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `a shortcode on its own line becomes a block passthrough`() {
        val block = parser.parse("{{< figure src=\"a.png\" >}}\n").blocks.single()

        val passthrough = block as RawPassthrough
        assertEquals("{{< figure src=\"a.png\" >}}", passthrough.text)
        assertEquals(Origin.HUGO_SHORTCODE_ANGLE, passthrough.origin)
    }

    @Test
    fun `the percent variant is tagged separately`() {
        // The two delimiters mean different things to Hugo, so they must come back as written.
        val passthrough = parser.parse("{{% notice warning %}}\n").blocks.single() as RawPassthrough

        assertEquals(Origin.HUGO_SHORTCODE_PERCENT, passthrough.origin)
    }

    @Test
    fun `an inline shortcode splits the text around it`() {
        val paragraph = parser.parse("Text before {{% x %}} after.\n").blocks.single() as Paragraph

        val raw = paragraph.inlines.filterIsInstance<RawInline>().single()
        assertEquals("{{% x %}}", raw.text)
        assertEquals("Text before {{% x %}} after.", paragraph.plainTextWithRaw())
    }

    @Test
    fun `two shortcodes in one paragraph both survive`() {
        val paragraph = parser.parse("A {{< one >}} B {{< two >}} C\n").blocks.single() as Paragraph

        assertEquals(
            listOf("{{< one >}}", "{{< two >}}"),
            paragraph.inlines.filterIsInstance<RawInline>().map { it.text },
        )
    }

    @Test
    fun `markup inside a shortcode is not interpreted`() {
        // The whole point of "opaque". Whatever the parser made of the innards is discarded.
        val paragraph = parser.parse("Before {{< ref \"a *b* c\" >}} after\n").blocks.single() as Paragraph

        val raw = paragraph.inlines.filterIsInstance<RawInline>().single()
        assertEquals("{{< ref \"a *b* c\" >}}", raw.text, "The asterisks must survive verbatim")
    }

    @Test
    fun `a multi-line shortcode is one span`() {
        val source = "{{< figure\n    src=\"a.png\"\n    alt=\"x\" >}}\n"

        val passthrough = parser.parse(source).blocks.single() as RawPassthrough
        assertEquals(source.trimEnd('\n'), passthrough.text)
    }

    @Test
    fun `two shortcodes do not merge into one greedy span`() {
        // A greedy regex would swallow everything between the first `{{<` and the last `>}}`.
        val paragraph = parser.parse("{{< a >}} middle {{< b >}}\n").blocks.single() as Paragraph

        val raws = paragraph.inlines.filterIsInstance<RawInline>()
        assertEquals(listOf("{{< a >}}", "{{< b >}}"), raws.map { it.text })
        assertTrue(
            paragraph.plainTextWithRaw().contains(" middle "),
            "The text between two shortcodes was swallowed",
        )
    }

    @Test
    fun `a shortcode inside a code fence keeps its literal text -- known Hugo divergence`() {
        // Hugo extracts shortcodes everywhere, code fences included, which is why a Hugo author has
        // to escape one to show it literally. This parser leaves code contents alone: CodeBlock.text
        // is a string and cannot hold an opaque span, and for an editor showing what the author
        // typed is right. The divergence has to be reconciled when export exists, in Phase 10.
        val source = "```\n{{< figure src=\"a.png\" >}}\n```\n"

        val code = parser.parse(source).blocks.single() as CodeBlock
        assertTrue(
            code.text.contains("{{< figure src=\"a.png\" >}}"),
            "Code block contents were rewritten: '${code.text}'",
        )
    }

    @Test
    fun `a paired shortcode is two opaque spans with Markdown between them`() {
        // hugo-markdown.md: both forms support a closing tag, and `{{% %}}` content "is processed
        // as Markdown". Treating each tag as its own span is what leaves the content parseable.
        val paragraph =
            parser.parse("{{% note %}}\nSome *emphasis* here.\n{{% /note %}}\n").blocks.single() as Paragraph

        val raws = paragraph.inlines.filterIsInstance<RawInline>()
        assertEquals(listOf("{{% note %}}", "{{% /note %}}"), raws.map { it.text })
        assertTrue(
            paragraph.inlines.any { it is Emphasis },
            "Content between paired tags should still parse as Markdown: ${paragraph.inlines}",
        )
    }

    @Test
    fun `a whitespace-trimming shortcode is recognised`() {
        // hugo-markdown.md notes the `{{<-` and `->}}` variants.
        val passthrough = parser.parse("{{<- figure ->}}\n").blocks.single() as RawPassthrough

        assertEquals("{{<- figure ->}}", passthrough.text)
    }

    @Test
    fun `a document with no shortcodes is unchanged`() {
        val document = parser.parse("Just a paragraph with {braces} and a < sign.\n")

        assertTrue(document.blocks.single() is Paragraph)
        assertTrue(
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<RawInline>()
                .isEmpty(),
        )
    }

    @Test
    fun `an unterminated shortcode is ordinary text`() {
        val document = parser.parse("An open {{< shortcode that never closes\n")

        assertTrue(
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<RawInline>()
                .isEmpty(),
            "An unclosed shortcode must not swallow the rest of the document",
        )
    }

    /** Text content including shortcode passthroughs, for checking nothing was dropped. */
    private fun Paragraph.plainTextWithRaw(): String =
        inlines.joinToString("") { inline ->
            when (inline) {
                is RawInline -> inline.text
                else -> listOf(inline).plainText()
            }
        }
}
