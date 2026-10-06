package com.appthere.drafts.editor.ui

/**
 * The ten-thousand-word document the recurring performance gate is measured on.
 *
 * `IMPLEMENTATION-PLAN.md`: "10,000-word fixture, slowest target device. Typing latency, scroll,
 * reparse bounds." Two of those three are measured in this module and both want the same document,
 * so it lives here rather than staying private to whichever test was written first.
 *
 * Headings, lists, quotes and inline markup, so that a careless dirty window has something to get
 * wrong. It mirrors the shape of `:editor-engine`'s own gate fixture without sharing it -- a module
 * existing only to hold a string generator would cost more than the duplication -- and the word
 * count assertion in `TypingLatencyTest` is what keeps either of them honest.
 */
internal fun gateDocument(): String =
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

private const val SECTIONS = 64
private const val PARAGRAPHS_PER_SECTION = 5

/**
 * The same gate on a screenplay (Phase 7): ten thousand words of Fountain, which the editor lays out
 * by 5.4's rules rather than 5.2's -- insets, one size of type, dual dialogue, notes.
 *
 * It opens with action, so that "type into the first block" means typing into a line of prose in
 * both fixtures rather than into a scene heading, which would change role at the first keystroke.
 */
internal fun gateScreenplay(): String =
    buildString {
        appendLine("The city wakes slowly, a long grey morning that nobody in it has asked for.")
        appendLine()
        repeat(SCENES) { scene ->
            appendLine("INT. APARTMENT $scene - DAY")
            appendLine()
            appendLine(
                "Rain against the window. MARA crosses the room with a cup of coffee she will not " +
                    "drink, and stops at the table where the letters are. [[Is this too slow?]]",
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
