package com.appthere.drafts.design

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrast, computed rather than eyeballed.
 *
 * The Phase 3 acceptance criteria are explicit: "All themes meet 4.5:1 body / 3:1 large; high
 * contrast meets 7:1, verified by test." A palette is the kind of thing that gets adjusted because
 * it looks nicer on the monitor of whoever adjusted it, and the person it stops being readable for
 * is never in the room. This is the check that is in the room.
 */
class PaletteTest {
    @Test
    fun `the contrast formula matches the values WCAG publishes`() {
        // Black on white is 21:1 and a colour against itself is 1:1 -- the two ends of the scale,
        // and enough to catch a formula that has drifted.
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), TOLERANCE)
        assertEquals(1.0, Contrast.ratio(Color.Black, Color.Black), TOLERANCE)

        // A published mid-scale pair: #767676 on white is the canonical "just passes 4.5:1" grey.
        val grey = Contrast.ratio(Color(0xFF767676), Color.White)
        assertTrue(grey >= Contrast.BODY, "#767676 on white should just clear 4.5:1, got $grey")
        assertTrue(grey < Contrast.BODY + 0.5, "#767676 on white should be near 4.5:1, got $grey")
    }

    @Test
    fun `ink is readable on every palette`() {
        Palettes.all.forEach { palette ->
            val ratio = Contrast.ratio(palette.ink, palette.background)
            assertTrue(
                ratio >= palette.minimumContrast,
                "${palette.id}: ink on background is ${round(ratio)}:1, below ${palette.minimumContrast}:1",
            )
        }
    }

    @Test
    fun `muted markup is readable on every palette`() {
        // Held to the body threshold, not the affordance one: fences and bullets are characters the
        // author can edit, so they have to be read rather than merely noticed.
        Palettes.all.forEach { palette ->
            val ratio = Contrast.ratio(palette.muted, palette.background)
            assertTrue(
                ratio >= palette.minimumContrast,
                "${palette.id}: muted on background is ${round(ratio)}:1, below ${palette.minimumContrast}:1",
            )
        }
    }

    @Test
    fun `the accent is readable on every palette`() {
        Palettes.all.forEach { palette ->
            val ratio = Contrast.ratio(palette.accent, palette.background)
            assertTrue(
                ratio >= palette.minimumContrast,
                "${palette.id}: accent on background is ${round(ratio)}:1, below ${palette.minimumContrast}:1",
            )
        }
    }

    @Test
    fun `every text colour is readable on the editing tint`() {
        Palettes.all.forEach { palette ->
            val text = listOf("ink" to palette.ink, "muted" to palette.muted, "accent" to palette.accent)
            text.forEach { (name, colour) ->
                val ratio = Contrast.ratio(colour, palette.editing)
                assertTrue(
                    ratio >= palette.minimumContrast,
                    "${palette.id}: $name on the editing tint is ${round(ratio)}:1, below ${palette.minimumContrast}:1",
                )
            }
        }
    }

    @Test
    fun `the editing tint can be told from the ground`() {
        // Its one job is to be seen. Too close to the ground and the line being edited looks like
        // any other, and its reflow on leaving comes as a surprise.
        Palettes.all.forEach { palette ->
            val ratio = Contrast.ratio(palette.editing, palette.background)
            assertTrue(ratio >= VISIBLE_TINT, "${palette.id}: the editing tint is ${round(ratio)}:1 from the ground")
        }
    }

    @Test
    fun `muted is quieter than ink without being unreadable`() {
        // The point of the colour: it has to read as decoration beside the text, which means less
        // contrast than the ink. A palette where the two are the same would pass every threshold
        // above and lose the distinction the preview depends on.
        Palettes.all.forEach { palette ->
            val ink = Contrast.ratio(palette.ink, palette.background)
            val muted = Contrast.ratio(palette.muted, palette.background)

            assertTrue(muted < ink, "${palette.id}: muted is not quieter than ink")
        }
    }

    @Test
    fun `high contrast is the most contrasting palette`() {
        val others = Palettes.all.filter { it != Palettes.HighContrast }
        val high = Contrast.ratio(Palettes.HighContrast.ink, Palettes.HighContrast.background)

        others.forEach { palette ->
            val ratio = Contrast.ratio(palette.ink, palette.background)
            assertTrue(high > ratio, "High contrast (${round(high)}:1) is not above ${palette.id}")
        }
    }

    @Test
    fun `the palettes are all distinct`() {
        assertEquals(
            Palettes.all.size,
            Palettes.all
                .map { it.id }
                .toSet()
                .size,
            "Two palettes share a name",
        )
        assertEquals(
            Palettes.all.size,
            Palettes.all
                .map { it.background }
                .toSet()
                .size,
            "Two palettes share a ground",
        )
    }

    private fun round(ratio: Double) = (ratio * ROUNDING).roundToInt() / ROUNDING

    private companion object {
        const val TOLERANCE = 0.01
        const val ROUNDING = 100.0

        /** Enough to see on any of the four grounds without drawing the eye from the text. */
        const val VISIBLE_TINT = 1.1
    }
}
