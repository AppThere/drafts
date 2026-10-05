package com.appthere.drafts.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.Measure
import com.appthere.drafts.design.MotionPreference
import com.appthere.drafts.design.Palettes
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.Theme
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.licences
import com.appthere.drafts.i18n.resources.notes
import com.appthere.drafts.i18n.resources.notes_collapsed
import com.appthere.drafts.i18n.resources.theme_dark
import com.appthere.drafts.i18n.resources.theme_high_contrast
import com.appthere.drafts.i18n.resources.theme_sepia
import com.appthere.drafts.i18n.resources.theme_system
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader controls of `appthere-drafts.md` 5.5, and the accessibility requirements 10.2 puts
 * on them.
 *
 * These controls are assistive technology -- the spec says so, naming line height and letter
 * spacing as dyslexia support and weight as low-vision support. A control that is unreachable, or
 * whose target is too small, or that silently refuses to move, fails the person it was built for
 * and nobody else notices.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderControlsTest {
    @Test
    fun `every control changes the setting it names`() {
        val changes =
            listOf(
                "Text size, increase" to { s: ReaderSettings -> s.base != ReaderSettings().base },
                "Line height, increase" to { s: ReaderSettings -> s.lineHeight != ReaderSettings().lineHeight },
                "Letter spacing, increase" to { s: ReaderSettings ->
                    s.letterSpacing != ReaderSettings().letterSpacing
                },
                "Line length, increase" to { s: ReaderSettings -> s.characters != ReaderSettings().characters },
                "Paragraph spacing, increase" to { s: ReaderSettings ->
                    s.paragraphSpacing != ReaderSettings().paragraphSpacing
                },
                "Body weight, increase" to { s: ReaderSettings -> s.bodyWeight != ReaderSettings().bodyWeight },
            )

        changes.forEach { (button, changed) ->
            runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
                var settings by mutableStateOf(ReaderSettings())
                setContent {
                    DraftsTheme(settings) { ReaderControls(settings, onChange = { settings = it }) }
                }

                onNodeWithContentDescription(button).performClick()

                assertTrue(changed(settings), "\"$button\" changed nothing")
            }
        }
    }

    @Test
    fun `a control will not go past its range`() {
        // 5.5 gives every control a range. Holding the button down at the end of one should stop,
        // not wrap around or run away -- and the clamping is the only thing standing between a
        // reader and a document they cannot read their way back from.
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            var settings by mutableStateOf(ReaderSettings())
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = { settings = it }) } }

            repeat(PAST_THE_END) { onNodeWithContentDescription("Text size, increase").performClick() }
            assertEquals(Prose.MaximumBase, settings.base)

            repeat(PAST_THE_END * 2) { onNodeWithContentDescription("Text size, decrease").performClick() }
            assertEquals(Prose.MinimumBase, settings.base)
        }
    }

    @Test
    fun `the theme can be changed`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            var settings by mutableStateOf(ReaderSettings())
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = { settings = it }) } }

            onNodeWithContentDescription("Theme, ${words(Res.string.theme_dark)}").performClick()

            assertEquals(Theme.Fixed(Palettes.Dark), settings.theme)
        }

    @Test
    fun `a screenplay's notes can be collapsed`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // 4.5: "Notes, Boneyard -- Dimmed, collapsible".
            var settings by mutableStateOf(ReaderSettings())
            setContent {
                DraftsTheme(settings) { ReaderControls(settings, onChange = { settings = it }, screenplay = true) }
            }

            onNodeWithContentDescription(
                "${words(Res.string.notes)}, ${words(Res.string.notes_collapsed)}",
            ).performClick()

            assertTrue(settings.collapseNotes)
        }

    @Test
    fun `prose has no notes to collapse`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val settings = ReaderSettings()
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = {}) } }

            onNodeWithContentDescription("${words(Res.string.notes)}, ${words(Res.string.notes_collapsed)}")
                .assertDoesNotExist()
        }

    @Test
    fun `the theme can follow the system`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // 5.5 lists "system" among the themes. It is a choice like the others, and has to be
            // reachable the same way.
            var settings by mutableStateOf(ReaderSettings())
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = { settings = it }) } }

            onNodeWithContentDescription("Theme, ${words(Res.string.theme_system)}").performClick()

            assertEquals(Theme.System, settings.theme)
        }

    @Test
    fun `reduced motion can be asked for without the OS saying so`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            var settings by mutableStateOf(ReaderSettings())
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = { settings = it }) } }

            onNodeWithContentDescription("Motion, Reduced").performClick()

            assertEquals(MotionPreference.Reduced, settings.motion)
        }

    @Test
    fun `every target is at least forty-eight dp`() {
        // 10.2: "Touch targets >= 48dp." Checked on every interactive node rather than a sample,
        // because the one that is too small will be the one nobody thought to measure.
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val settings = ReaderSettings()
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = {}) } }

            val targets =
                listOf(
                    "Text size, increase",
                    "Text size, decrease",
                    "Theme, ${words(Res.string.theme_sepia)}",
                    "Motion, Full",
                )

            targets.forEach { description ->
                onNodeWithContentDescription(description)
                    .assertWidthIsAtLeast(TOUCH_TARGET)
                    .assertHeightIsAtLeast(TOUCH_TARGET)
            }
        }
    }

    @Test
    fun `each control announces its value along with its name`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // A screen reader that says "18sp" has not said what is 18sp. The label carries both.
            val settings = ReaderSettings(base = 22.sp)
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = {}) } }

            assertEquals(1, onAllNodesWithContentDescription("Text size, 22sp").fetchSemanticsNodes().size)
        }

    @Test
    fun `the measure control spans the range the spec allows`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            var settings by mutableStateOf(ReaderSettings())
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = { settings = it }) } }

            repeat(PAST_THE_END) { onNodeWithContentDescription("Line length, decrease").performClick() }
            assertEquals(Measure.MINIMUM_CHARACTERS, settings.characters)

            repeat(PAST_THE_END * 2) { onNodeWithContentDescription("Line length, increase").performClick() }
            assertEquals(Measure.MAXIMUM_CHARACTERS, settings.characters)
        }

    @Test
    fun `every control survives two hundred percent system font scale`() =
        runSkikoComposeUiTest(size = Size(SNUG, TALL), density = Density(1f, fontScale = 2f)) {
            // Sized to the panel rather than to a generous window, because a generous window hides
            // the failure: a row that overflows the panel still fits on a desk-sized screen. Here
            // the theme options have to wrap or the last of them is off the edge.
            //
            // A Phase 3 acceptance criterion, and the panel failed it. Its widths were fixed dp
            // while 10.2 requires `sp` for all text, so at 200% the labels doubled and the frame did
            // not: "High contrast" was set one letter to a line down the side, and the four
            // controls below "Letter spacing" were clipped away entirely -- motion among them,
            // which someone reading at 200% is more likely than most to want.
            val settings = ReaderSettings()
            setContent {
                DraftsTheme(settings) {
                    ReaderControls(
                        settings,
                        onChange = {},
                        links = { PanelLink(words(Res.string.licences), onClick = {}) },
                    )
                }
            }

            listOf(
                "Text size, increase",
                "Line height, increase",
                "Letter spacing, increase",
                "Line length, increase",
                "Body weight, increase",
                "Theme, ${words(Res.string.theme_high_contrast)}",
                "Motion, Reduced",
                "Licences",
            ).forEach { control ->
                // Bounds, not `assertIsDisplayed`: a node hanging off the edge of the window is
                // still "displayed" if any sliver of it shows, which is exactly the state a theme
                // name in an unwrapped row ends up in.
                val bounds = onNodeWithContentDescription(control).assertIsDisplayed().getBoundsInRoot()

                assertTrue(bounds.left.value >= 0f, "$control starts at ${bounds.left}, off the left")
                assertTrue(bounds.right.value <= SNUG, "$control reaches ${bounds.right}, past the window")
            }
        }

    @Test
    fun `the panel fits a compact window at two hundred percent`() =
        runSkikoComposeUiTest(size = Size(COMPACT, TALL), density = Density(1f, fontScale = 2f)) {
            // The worst case the app actually has to survive, and not a contrived one: a phone,
            // which 6 calls Compact, belonging to someone who has turned their system text up --
            // which is exactly the person most likely to open these controls. The label column
            // alone takes most of the width, so the theme options have to wrap or the last of them
            // is off the side of the screen.
            val settings = ReaderSettings()
            setContent {
                DraftsTheme(settings) {
                    ReaderControls(
                        settings,
                        onChange = {},
                        links = { PanelLink(words(Res.string.licences), onClick = {}) },
                    )
                }
            }

            listOf("Theme, ${words(Res.string.theme_high_contrast)}", "Text size, increase", "Motion, Reduced")
                .forEach { control ->
                    val bounds = onNodeWithContentDescription(control).getBoundsInRoot()

                    assertTrue(bounds.right.value <= COMPACT, "$control reaches ${bounds.right} of ${COMPACT}dp")
                }
        }

    @Test
    fun `the panel scrolls when it is taller than the window`() =
        runSkikoComposeUiTest(size = Size(SNUG, SHORT), density = Density(1f, fontScale = 2f)) {
            // Eight controls at twice the size do not fit a laptop screen, and a panel that cannot
            // scroll simply stops -- the controls past the bottom edge are not reachable at all.
            // `performScrollTo` fails outright if there is no scrollable ancestor, so this asserts
            // both that the panel scrolls and that the last control can be got to.
            val settings = ReaderSettings()
            setContent {
                DraftsTheme(settings) {
                    ReaderControls(
                        settings,
                        onChange = {},
                        links = { PanelLink(words(Res.string.licences), onClick = {}) },
                    )
                }
            }

            onNodeWithContentDescription("Licences").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `labels still fit on one line at two hundred percent`() {
        // The other half of the 200% fix, and the half a displayed-or-not check cannot see. With a
        // fixed-width frame everything was still *reachable* -- it just read badly: "Line height"
        // broke across two lines and the letter-spacing value came out as "0.00e / m". A label that
        // doubles in size and stays on one line is a frame that followed it.
        val single = labelHeightAt(scale = 1f)
        val doubled = labelHeightAt(scale = 2f)

        assertTrue(
            doubled < single * WRAPPED,
            "The label went from ${single}dp to ${doubled}dp, which is more than twice -- it wrapped",
        )
    }

    private fun labelHeightAt(scale: Float): Float {
        var height = 0f
        runSkikoComposeUiTest(size = Size(WIDE, TALL), density = Density(1f, fontScale = scale)) {
            val settings = ReaderSettings()
            setContent { DraftsTheme(settings) { ReaderControls(settings, onChange = {}) } }
            height =
                onNodeWithContentDescription("Letter spacing, 0.00em")
                    .getBoundsInRoot()
                    .let { it.bottom - it.top }
                    .value
        }
        return height
    }

    private companion object {
        const val WIDTH = 900f
        const val HEIGHT = 900f

        /** Room enough that nothing is off-screen for reasons other than the panel. */
        const val WIDE = 1400f

        /** Only as wide as the panel: an overflowing row has nowhere to hide. */
        const val SNUG = 900f

        /** A phone, which 6 calls Compact. */
        const val COMPACT = 420f

        /** A laptop's worth of height, where eight doubled controls do not fit. */
        const val SHORT = 520f
        const val TALL = 1400f

        /** Twice the scale should double a label's height; anything more means it wrapped. */
        const val WRAPPED = 2.6f

        /** More presses than any range needs, so the end is reached whatever the step size. */
        const val PAST_THE_END = 40

        val TOUCH_TARGET = 48.dp
    }
}
