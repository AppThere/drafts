package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.EmphasisDelimiter
import com.appthere.drafts.core.model.Image
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.LinkForm
import com.appthere.drafts.core.model.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Inline lowering: emphasis, code spans, links, images. */
class InlineLoweringTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `emphasis records the delimiter the author used`() {
        // Round-trip contract item 3, and the one Phase 1's acceptance names explicitly.
        val asterisk = parser.parse("*emphasis*").firstInline<Emphasis>()
        val underscore = parser.parse("_emphasis_").firstInline<Emphasis>()

        assertEquals(EmphasisDelimiter.ASTERISK, asterisk.delimiter)
        assertEquals(EmphasisDelimiter.UNDERSCORE, underscore.delimiter)
        assertFalse(asterisk.strong)
    }

    @Test
    fun `strong emphasis is marked strong`() {
        val strong = parser.parse("**bold**").firstInline<Emphasis>()

        assertTrue(strong.strong)
        assertEquals("bold", strong.children.plainText())
    }

    @Test
    fun `emphasis markers are filtered out of the content`() {
        // The CST keeps the `*` characters as children. Lowering drops them, once, here -- if it
        // did not, every backend downstream would have to.
        val emphasis = parser.parse("*text*").firstInline<Emphasis>()

        assertEquals("text", emphasis.children.plainText(), "Markers must not survive lowering")
    }

    @Test
    fun `a code span records its backtick run length`() {
        val single = parser.parse("`code`").firstInline<CodeSpan>()
        val double = parser.parse("``has ` backtick``").firstInline<CodeSpan>()

        assertEquals(1, single.backtickCount)
        assertEquals(2, double.backtickCount, "A span containing a backtick needs a longer fence")
    }

    @Test
    fun `a code span keeps a backtick that its fence was widened to protect`() {
        // The inner backtick is itself a BACKTICK token, so filtering by token type would delete
        // the one character the longer fence exists for.
        val span = parser.parse("``has ` backtick``").firstInline<CodeSpan>()

        assertTrue(span.text.contains('`'), "Actual: '${span.text}'")
    }

    @Test
    fun `an inline link carries href and title`() {
        val link = parser.parse("""[text](/url "Title")""").firstInline<Link>()

        assertEquals("/url", link.href)
        assertEquals("Title", link.title)
        assertEquals(LinkForm.Inline, link.form)
        assertEquals("text", link.children.plainText())
    }

    @Test
    fun `a reference link resolves its href from a definition`() {
        val link = parser.parse("[text][ref]\n\n[ref]: /target \"T\"").firstInline<Link>()

        assertEquals("/target", link.href)
        assertEquals(LinkForm.Reference("ref"), link.form)
    }

    @Test
    fun `a definition may follow the reference that uses it`() {
        // CommonMark allows forward references, which is why collection is a separate first pass.
        assertEquals("/found", parser.parse("[text][late]\n\n[late]: /found").firstInline<Link>().href)
    }

    @Test
    fun `reference labels match case insensitively and collapse whitespace`() {
        val link = parser.parse("[text][FOO BAR]\n\n[foo   bar]: /normalised").firstInline<Link>()

        assertEquals("/normalised", link.href)
    }

    @Test
    fun `an unresolved reference stays literal text`() {
        // CommonMark: a reference with no definition is not a link, it is text -- brackets and all.
        // The CST commits to a link node before definitions are known, so the lowering unmakes that
        // decision. An earlier version of this test asserted the opposite; it was wrong, and the
        // conformance run is what showed it.
        val document = parser.parse("[text][missing]")

        assertTrue(
            document.blocks
                .flatMap { it.inlinesOf() }
                .filterIsInstance<Link>()
                .isEmpty(),
            "An unresolved reference must not become a link",
        )
        assertEquals("[text][missing]", (document.blocks.single() as Paragraph).plainText())
    }

    @Test
    fun `an autolink lowers as an autolink`() {
        val link = parser.parse("<https://example.com>").firstInline<Link>()

        assertEquals("https://example.com", link.href)
        assertEquals(LinkForm.Autolink(linkified = false), link.form)
    }

    @Test
    fun `an image carries src alt and title`() {
        val image = parser.parse("""![Alt text](/img.png "Caption")""").firstInline<Image>()

        assertEquals("/img.png", image.src)
        assertEquals("Alt text", image.alt)
        assertEquals("Caption", image.title)
    }
}
