package com.appthere.drafts.editor.engine

/**
 * The document the Phase 2 gate criteria are measured against.
 *
 * Ten thousand words, with the structures that make reparse interesting: headings, lists, quotes,
 * and inline markup a careless window could split. A bounded reparse looks identical to an
 * unbounded one on three paragraphs, which is why the criteria specify a size.
 */
internal object GateFixture {
    fun tenThousandWords(): String =
        buildString {
            repeat(SECTIONS) { section ->
                appendLine("## Section $section")
                appendLine()
                repeat(PARAGRAPHS_PER_SECTION) { paragraph ->
                    appendLine(
                        "Paragraph $paragraph of section $section with *emphasis*, `code`, and a " +
                            "[link](https://example.com) in it, written out at enough length to " +
                            "make the document a realistic size for the gate criterion.",
                    )
                    appendLine()
                }
                appendLine("- first item")
                appendLine("- second item")
                appendLine()
                appendLine("> A quoted line.")
                appendLine()
            }
        }

    private const val SECTIONS = 60
    private const val PARAGRAPHS_PER_SECTION = 5
}
