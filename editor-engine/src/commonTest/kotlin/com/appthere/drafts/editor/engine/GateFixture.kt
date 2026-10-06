package com.appthere.drafts.editor.engine

/**
 * The document the Phase 2 gate criteria are measured against.
 *
 * Ten thousand words, with the structures that make reparse interesting: headings, lists, quotes,
 * and inline markup a careless window could split. A bounded reparse looks identical to an
 * unbounded one on three paragraphs, which is why the criteria specify a size.
 *
 * The section count is set so the document clears ten thousand words rather than approaches it. It
 * was sixty for a while, which gives 9,480 -- close enough to look right in a name and not close
 * enough to be one.
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

    /**
     * The same size of screenplay, for Phase 7: scenes, speeches, a dual pair and a note in each, so
     * that Fountain's chunk-widened window has its positional cases to get wrong.
     */
    fun tenThousandWordScreenplay(): String =
        buildString {
            repeat(SCENES) { scene ->
                appendLine("INT. APARTMENT $scene - DAY")
                appendLine()
                appendLine(
                    "Rain against the window. MARA crosses the room with a cup of coffee she will " +
                        "not drink, and stops at the table where the letters are. [[Is this too slow?]]",
                )
                appendLine()
                appendLine("MARA")
                appendLine("(quietly)")
                appendLine("You kept every one of them. I thought you had thrown them away years ago.")
                appendLine()
                appendLine("JONAH")
                appendLine("I meant to. I kept meaning to, and then it was easier to leave them where they were.")
                appendLine()
                appendLine("MARA ^")
                appendLine("Easier for you, maybe.")
                appendLine()
                appendLine("She sets the cup down. He does not look up from the window.")
                appendLine()
                appendLine("CUT TO:")
                appendLine()
            }
        }

    private const val SCENES = 112
    private const val SECTIONS = 64
    private const val PARAGRAPHS_PER_SECTION = 5
}
