package com.appthere.drafts.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 5.5: "Theme: light, dark, sepia, high contrast, system". */
class ThemeTest {
    @Test
    fun `system follows the system's preference`() {
        assertEquals(Palettes.Dark, Theme.System.paletteFor(systemPrefersDark = true))
        assertEquals(Palettes.Light, Theme.System.paletteFor(systemPrefersDark = false))
    }

    @Test
    fun `a chosen palette ignores the system`() {
        // A reader who picked sepia for a long session has not asked for it to turn dark at dusk.
        val sepia = Theme.Fixed(Palettes.Sepia)

        assertEquals(Palettes.Sepia, sepia.paletteFor(systemPrefersDark = true))
        assertEquals(Palettes.Sepia, sepia.paletteFor(systemPrefersDark = false))
    }

    @Test
    fun `every theme 5_5 lists is offered`() {
        assertEquals(listOf("Light", "Dark", "Sepia", "High contrast", "System"), Theme.all.map { it.name })
    }

    @Test
    fun `every theme is found again by its name`() {
        // The name is what goes to disk, so a theme that could not be looked up by it would be
        // forgotten every time the application closed.
        Theme.all.forEach { assertEquals(it, Theme.named(it.name)) }
    }

    @Test
    fun `a name no theme has finds nothing`() {
        assertNull(Theme.named("Solarised"))
    }
}
