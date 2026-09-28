package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 5.5's "Paragraph spacing", measured on the page rather than in the settings.
 *
 * The setting is only worth having if the document moves when it does. What the reader should see
 * is paragraphs further apart -- and, because the whole scale's spacing moves together, headings
 * still set off by more than the paragraphs around them.
 */
@OptIn(ExperimentalTestApi::class)
class ParagraphSpacingTest {
    @Test
    fun `doubling the setting doubles the space between paragraphs`() {
        val default = gapBetween(FIRST, SECOND, ReaderSettings())
        val doubled = gapBetween(FIRST, SECOND, ReaderSettings(paragraphSpacing = DOUBLE_THE_DEFAULT))

        assertTrue(
            abs(doubled / default - 2f) < TOLERANCE,
            "The gap went from ${default}dp to ${doubled}dp, which is not twice",
        )
    }

    @Test
    fun `a heading keeps more space above it than a paragraph at the widest setting`() {
        // Were only paragraphs to move, the widest setting would give the gap between two
        // paragraphs as much air as the gap before a heading, and the reader skimming for the next
        // section would lose the thing that marks one.
        val widest = ReaderSettings(paragraphSpacing = ReaderSettings.MAXIMUM_PARAGRAPH_SPACING)

        val betweenParagraphs = gapBetween(FIRST, SECOND, widest)
        val beforeHeading = gapBetween(SECOND, HEADING, widest)

        assertTrue(
            beforeHeading > betweenParagraphs,
            "A heading gets ${beforeHeading}dp above it and a paragraph ${betweenParagraphs}dp",
        )
    }

    private fun gapBetween(
        upper: String,
        lower: String,
        settings: ReaderSettings,
    ): Float {
        var gap = 0f
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            show(settings)
            gap = (onNodeWithText(lower).getBoundsInRoot().top - onNodeWithText(upper).getBoundsInRoot().bottom).value
        }
        return gap
    }

    private fun SkikoComposeUiTest.show(settings: ReaderSettings) {
        val state = EditorState(DocumentSession("$FIRST\n\n$SECOND\n\n## $HEADING\n\n$LAST\n"))
        setContent { DraftsTheme(settings) { BlockEditor(state = state) } }
    }

    private companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 900f

        const val FIRST = "A first paragraph."
        const val SECOND = "A second paragraph."
        const val HEADING = "A section"
        const val LAST = "A final paragraph."

        /** 5.2's 0.75em, twice. */
        const val DOUBLE_THE_DEFAULT = 1.5f

        /** Rounding to whole pixels, and nothing more. */
        const val TOLERANCE = 0.1f
    }
}
