package com.appthere.drafts.app.desktop

/**
 * A document to open the spike on.
 *
 * Chosen to exercise what 4.1 says reveal must and must not change: headings of several levels,
 * inline emphasis and code, a list, a quote, and a fenced block. Clicking into any of them should
 * show its markup without the line moving.
 */
internal object SampleDocument {
    val TEXT: String =
        """
        # Drafts

        A *What You See Is What You Mean* editor. Click into any block to reveal its
        markup; click away and it returns to preview. The line should not move.

        ## Reveal and preview

        This paragraph has *emphasis*, **strong emphasis**, `inline code`, a
        [link](https://example.com), and ~~something struck out~~.

        ### What stays constant

        - Heading size, in both states
        - Typeface, in both states
        - Block indentation and alignment

        > Block identity is stable; inline decoration is what reveals.

        ```kotlin
        fun main() {
            println("Code blocks stay monospace in both states.")
        }
        ```

        The paragraph below is here to give the caret somewhere to go when testing
        movement across block boundaries.

        A final paragraph.
        """.trimIndent() + "\n"
}
