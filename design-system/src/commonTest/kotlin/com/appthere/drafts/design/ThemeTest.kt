package com.appthere.drafts.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        // Identifiers, not labels. 5.5 lists five themes and these are the keys they are saved
        // under; what a reader sees is a separate string that 11.1 will translate.
        assertEquals(listOf("light", "dark", "sepia", "high-contrast", "system"), Theme.all.map { it.id })
    }

    @Test
    fun `a theme saved before the split still resolves`() {
        // Identifiers and labels used to be one string, so settings files exist that say "Dark" and
        // "High contrast". A reader changing version should not find their theme reset.
        assertEquals(Theme.Fixed(Palettes.Dark), Theme.withId("Dark"))
        assertEquals(Theme.Fixed(Palettes.HighContrast), Theme.withId("High contrast"))
        assertEquals(Theme.System, Theme.withId("System"))
    }

    @Test
    fun `an identifier is not a word anybody reads`() {
        // The guard on the whole split: if an id ever looks like prose again, it is being used as
        // a label again, and translating it would lose every saved theme.
        Theme.all.forEach {
            assertEquals(it.id.lowercase(), it.id, "${it.id} is capitalised like a label")
            assertTrue(' ' !in it.id, "${it.id} has a space in it")
        }
    }

    @Test
    fun `every theme is found again by its name`() {
        // The name is what goes to disk, so a theme that could not be looked up by it would be
        // forgotten every time the application closed.
        Theme.all.forEach { assertEquals(it, Theme.withId(it.id)) }
    }

    @Test
    fun `a name no theme has finds nothing`() {
        assertNull(Theme.withId("solarised"))
    }
}
