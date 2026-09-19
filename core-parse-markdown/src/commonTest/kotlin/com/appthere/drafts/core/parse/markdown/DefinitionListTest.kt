package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.DefinitionList
import com.appthere.drafts.core.model.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Definition lists (`markdown-dialect.md` 5).
 *
 * The plan assigns this fixture set to us: "No published suite exists for definition lists or
 * attributes; write them." So each case here is a sentence of 5 turned into an assertion, and the
 * negative cases matter as much as the positive ones -- this construct's failure mode is claiming
 * paragraphs that were never meant to be definitions.
 */
class DefinitionListTest {
    private val parser = MarkdownDocumentParser()

    @Test
    fun `a term and one definition become a definition list`() {
        val list = parser.parse("Term\n: Definition text\n").blocks.single() as DefinitionList

        val entry = list.entries.single()
        assertEquals("Term", entry.term.plainText())
        assertEquals(
            "Definition text",
            entry.definitions
                .single()
                .single()
                .plainTextOfBlock(),
        )
    }

    @Test
    fun `one term may have several definitions`() {
        // 5: "One or more definitions may follow a term."
        val list = parser.parse("Term\n: First\n: Second\n").blocks.single() as DefinitionList

        val entry = list.entries.single()
        assertEquals(2, entry.definitions.size)
        assertEquals(
            listOf("First", "Second"),
            entry.definitions.map { it.single().plainTextOfBlock() },
        )
    }

    @Test
    fun `a blank line between term and definition makes the list loose`() {
        // 5: "A blank line between term and definition makes the list loose (definitions wrapped
        // in <p>)." The parser gives two paragraphs rather than one, which is how it is detected.
        val tight = parser.parse("Term\n: Definition\n").blocks.single() as DefinitionList
        val loose = parser.parse("Term\n\n: Definition\n").blocks.single() as DefinitionList

        assertFalse(tight.entries.single().loose)
        assertTrue(loose.entries.single().loose)
    }

    @Test
    fun `a colon line with no preceding term is a paragraph`() {
        // 5 states this directly, and it is the rule that stops the construct eating ordinary prose
        // that happens to begin with a colon.
        val block = parser.parse(": orphan definition\n").blocks.single()

        assertTrue(block is Paragraph, "Expected a paragraph, got ${block::class.simpleName}")
    }

    @Test
    fun `the definition marker is removed from the content`() {
        val list = parser.parse("Term\n: Definition\n").blocks.single() as DefinitionList

        assertEquals(
            "Definition",
            list.entries
                .single()
                .definitions
                .single()
                .single()
                .plainTextOfBlock(),
            "The `: ` marker must not survive into the definition body",
        )
    }

    @Test
    fun `the term keeps its inline markup`() {
        // "The term is a single line of inline content", so emphasis in a term is emphasis.
        val list = parser.parse("A *term*\n: Definition\n").blocks.single() as DefinitionList

        assertEquals(
            "A term",
            list.entries
                .single()
                .term
                .plainText(),
        )
    }

    @Test
    fun `an ordinary two-line paragraph is not a definition list`() {
        val block = parser.parse("First line\nSecond line\n").blocks.single()

        assertTrue(block is Paragraph)
    }

    @Test
    fun `a term with no definition is not a definition list`() {
        assertTrue(parser.parse("Just a term\n").blocks.single() is Paragraph)
    }

    @Test
    fun `a colon without following whitespace is not a definition`() {
        // `:no-space` is prose -- a time, a ratio, a namespace. 5 requires "a `:` followed by
        // whitespace".
        assertTrue(parser.parse("Term\n:nospace\n").blocks.single() is Paragraph)
    }

    @Test
    fun `a definition list carries a source span covering both lines`() {
        val source = "Term\n: Definition\n"
        val list = parser.parse(source).blocks.single() as DefinitionList

        val span = requireNotNull(list.source)
        assertTrue(
            source.substring(span.start.value, span.endExclusive.value).contains(": Definition"),
            "The span should cover the definitions as well as the term",
        )
    }
}

private fun com.appthere.drafts.core.model.Block.plainTextOfBlock(): String =
    (this as? Paragraph)?.plainText().orEmpty()
