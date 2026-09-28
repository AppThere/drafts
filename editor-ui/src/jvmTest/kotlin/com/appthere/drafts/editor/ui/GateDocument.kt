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
