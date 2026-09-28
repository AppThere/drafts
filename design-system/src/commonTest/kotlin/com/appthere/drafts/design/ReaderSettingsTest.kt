package com.appthere.drafts.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reader controls of `appthere-drafts.md` 5.5, and the bands of 6.
 *
 * Every control here exists for someone: the line-height and letter-spacing sliders are named in
 * the spec as dyslexia support and the weight control as low-vision support. A control that
 * silently accepts a value outside its range is one that can put the document somewhere the reader
 * cannot get back from, so the clamping is the part worth testing.
 */
class ReaderSettingsTest {
    @Test
    fun `the defaults are the ones the spec describes`() {
        val settings = ReaderSettings()

        assertEquals(Prose.DefaultBase, settings.base)
        assertEquals(Theme.Fixed(Palettes.Light), settings.theme)
        assertEquals(ReaderSettings.DEFAULT_BODY_WEIGHT, settings.bodyWeight)
        assertFalse(settings.reducedMotion)
    }

    @Test
    fun `the defaults survive being clamped`() {
        // The bug this caught: the line-height control defaulted to 1.0 while its own range starts
        // at 1.3, so clamping silently moved it and every document rendered at 2.08 line height
        // instead of the 1.6 that 5.2 specifies. A default outside its own range is always a bug;
        // it just usually announces itself less politely than by making the whole app too airy.
        val defaults = ReaderSettings()

        assertEquals(defaults, defaults.clamped(), "Clamping changed the defaults")
    }

    @Test
    fun `the default line height is the one the prose scale sets for body`() {
        assertEquals(Prose.Body.lineHeight, ReaderSettings().lineHeight)
    }

    @Test
    fun `every control is held to its range`() {
        val wild =
            ReaderSettings(
                base = 100.sp,
                lineHeight = 9f,
                letterSpacing = 9f,
                characters = 500f,
                bodyWeight = 900,
                paragraphSpacing = 9f,
            ).clamped()

        assertEquals(Prose.MaximumBase, wild.base)
        assertEquals(ReaderSettings.MAXIMUM_LINE_HEIGHT, wild.lineHeight)
        assertEquals(ReaderSettings.MAX_LETTER_SPACING, wild.letterSpacing)
        assertEquals(Measure.MAXIMUM_CHARACTERS, wild.characters)
        assertEquals(ReaderSettings.MAX_BODY_WEIGHT, wild.bodyWeight)
        assertEquals(ReaderSettings.MAXIMUM_PARAGRAPH_SPACING, wild.paragraphSpacing)
    }

    @Test
    fun `paragraph spacing never reaches zero`() {
        // 5.2: "Paragraphs are separated by space, not first-line indent." With no space and no
        // indent, two paragraphs are one wall of text.
        val closed = ReaderSettings(paragraphSpacing = 0f).clamped()

        assertEquals(ReaderSettings.MINIMUM_PARAGRAPH_SPACING, closed.paragraphSpacing)
        assertTrue(closed.paragraphSpacing > 0f)
    }

    @Test
    fun `clamping leaves a reasonable setting alone`() {
        val chosen =
            ReaderSettings(
                base = 22.sp,
                lineHeight = 1.5f,
                letterSpacing = 0.04f,
                characters = 70f,
                bodyWeight = 350,
            )

        assertEquals(chosen, chosen.clamped())
    }

    @Test
    fun `reduced motion makes everything instant`() {
        assertTrue(Motion.of(reducedMotion = true).isInstant)
        assertFalse(Motion.of(reducedMotion = false).isInstant)
        assertEquals(REVEAL_MILLIS, Motion.Standard.revealMillis, "4.2 specifies a 120ms cross-fade")
    }

    @Test
    fun `window bands fall where the spec puts them`() {
        assertEquals(WidthClass.Compact, WindowSize.of(599.dp, 800.dp).width)
        assertEquals(WidthClass.Medium, WindowSize.of(600.dp, 800.dp).width)
        assertEquals(WidthClass.Medium, WindowSize.of(839.dp, 800.dp).width)
        assertEquals(WidthClass.Expanded, WindowSize.of(840.dp, 800.dp).width)
    }

    @Test
    fun `a phone in landscape is compact in height and hides its chrome`() {
        // 6: "a phone in landscape is Compact-height, where auto-hiding chrome is worth more than
        // anywhere else". Its *width* in landscape may well be Medium, so height has to be checked
        // on its own or the case is missed entirely.
        val landscape = WindowSize.of(width = 740.dp, height = 360.dp)

        assertEquals(HeightClass.Compact, landscape.height)
        assertTrue(landscape.autoHidesChrome)
    }

    @Test
    fun `two panes need width and height both`() {
        assertTrue(WindowSize.of(1200.dp, 1000.dp).allowsTwoPanes)
        assertFalse(WindowSize.of(1200.dp, 400.dp).allowsTwoPanes, "A short window has no room for two panes")
        assertFalse(WindowSize.of(700.dp, 1000.dp).allowsTwoPanes, "A medium window is single-document")
    }

    @Test
    fun `the measure follows the reader's character count`() {
        val narrow = Measure.of(3000.dp, 18.dp, characters = Measure.MINIMUM_CHARACTERS)
        val wide = Measure.of(3000.dp, 18.dp, characters = Measure.MAXIMUM_CHARACTERS)

        assertTrue(wide.contentWidth > narrow.contentWidth, "85 characters should be wider than 55")
    }

    private companion object {
        const val REVEAL_MILLIS = 120
    }
}
