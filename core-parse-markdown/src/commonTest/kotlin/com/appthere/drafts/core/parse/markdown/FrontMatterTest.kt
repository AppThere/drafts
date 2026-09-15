package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.FrontMatterFormat
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.ThematicBreak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Front matter (`markdown-dialect.md`, "Handled outside the Markdown parser").
 *
 * The collision test is the one that matters most: YAML's `---` is also CommonMark's thematic
 * break, and a document that opens with metadata must not lose it to a horizontal rule.
 */
class FrontMatterTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `YAML front matter is extracted and the body still parses`() {
        val document = parser.parse("---\ntitle: Test\nauthor: Someone\n---\n\n# Heading\n")

        val frontMatter = requireNotNull(document.frontMatter)
        assertEquals(FrontMatterFormat.YAML, frontMatter.format)
        assertEquals("title: Test\nauthor: Someone\n", frontMatter.text)
        assertTrue(document.blocks.filterIsInstance<Heading>().isNotEmpty(), "Body did not parse")
    }

    @Test
    fun `YAML delimiters do not become thematic breaks`() {
        // The specific failure markdown-dialect.md warns about.
        val document = parser.parse("---\ntitle: Test\n---\n\nBody text.\n")

        assertTrue(
            document.blocks.filterIsInstance<ThematicBreak>().isEmpty(),
            "Front matter delimiters were parsed as horizontal rules: ${document.blocks}",
        )
    }

    @Test
    fun `TOML front matter is recognised and tagged as TOML`() {
        // Never normalised between formats: TOML stays TOML.
        val document = parser.parse("+++\ntitle = \"Test\"\n+++\n\nBody.\n")

        val frontMatter = requireNotNull(document.frontMatter)
        assertEquals(FrontMatterFormat.TOML, frontMatter.format)
        assertEquals("title = \"Test\"\n", frontMatter.text)
    }

    @Test
    fun `JSON front matter is recognised`() {
        val document = parser.parse("{\n  \"title\": \"Test\"\n}\n\nBody.\n")

        val frontMatter = requireNotNull(document.frontMatter)
        assertEquals(FrontMatterFormat.JSON, frontMatter.format)
        assertEquals("{\n  \"title\": \"Test\"\n}", frontMatter.text)
    }

    @Test
    fun `a brace inside a JSON string does not end the block early`() {
        val document = parser.parse("{\n  \"title\": \"a } brace\"\n}\n\nBody.\n")

        assertEquals("{\n  \"title\": \"a } brace\"\n}", requireNotNull(document.frontMatter).text)
    }

    @Test
    fun `an escaped quote inside a JSON string is honoured`() {
        val document = parser.parse("{\n  \"title\": \"say \\\" then }\"\n}\n\nBody.\n")

        assertEquals(
            "{\n  \"title\": \"say \\\" then }\"\n}",
            requireNotNull(document.frontMatter).text,
        )
    }

    @Test
    fun `a document with no front matter has none`() {
        assertNull(parser.parse("# Just a heading\n").frontMatter)
    }

    @Test
    fun `a thematic break partway down the file is still a thematic break`() {
        // Only offset 0 counts. Treating a later `---` as metadata would delete someone's prose.
        val document = parser.parse("Some text.\n\n---\n\nMore text.\n")

        assertNull(document.frontMatter)
        assertTrue(document.blocks.filterIsInstance<ThematicBreak>().isNotEmpty())
    }

    @Test
    fun `an unterminated fence is not front matter`() {
        // A file that opens with `---` and never closes it is a thematic break followed by prose,
        // not metadata that swallows the whole document.
        val document = parser.parse("---\ntitle: never closed\n\nBody.\n")

        assertNull(document.frontMatter)
    }

    @Test
    fun `a delimiter with trailing content is not front matter`() {
        assertNull(parser.parse("---nope\ntitle: x\n---\n").frontMatter)
    }

    @Test
    fun `block offsets still index into the original source`() {
        // Masking rather than stripping is what keeps this true, and every byte-preserving save
        // depends on it.
        val source = "---\ntitle: Test\n---\n\n# Heading\n"
        val document = parser.parse(source)

        val heading = document.blocks.filterIsInstance<Heading>().single()
        val span = requireNotNull(heading.source)

        assertEquals("# Heading", source.substring(span.start.value, span.endExclusive.value))
    }

    @Test
    fun `front matter content is not parsed as Markdown`() {
        // It is metadata, kept verbatim. A `# ` inside it is a TOML comment, not a heading.
        val document = parser.parse("+++\n# a toml comment\ntitle = \"x\"\n+++\n\nBody.\n")

        assertEquals(
            emptyList(),
            document.blocks.filterIsInstance<Heading>(),
            "Front matter content leaked into the document body",
        )
        assertTrue(document.blocks.filterIsInstance<Paragraph>().isNotEmpty())
    }
}
