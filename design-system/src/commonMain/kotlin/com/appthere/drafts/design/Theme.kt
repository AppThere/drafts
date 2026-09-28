package com.appthere.drafts.design

import androidx.compose.runtime.Immutable

/**
 * What the reader chose in 5.5's theme control: "light, dark, sepia, high contrast, system".
 *
 * Four of those are a [Palette] and one is not. "System" is a promise to follow the operating
 * system's light or dark preference, including when it changes while the document is open, so it
 * cannot be resolved to a palette when it is chosen -- only when it is drawn. That is why the
 * choice and the palette are separate values: [ReaderSettings] holds the choice, and [DraftsTheme]
 * turns it into the palette in force.
 *
 * [name] is what goes to disk, and is stable across versions for that reason.
 */
@Immutable
sealed interface Theme {
    val name: String

    /** The palette this choice means, given whether the system currently prefers dark. */
    fun paletteFor(systemPrefersDark: Boolean): Palette

    /** One of the palettes, whatever the system prefers. */
    data class Fixed(
        val palette: Palette,
    ) : Theme {
        override val name: String get() = palette.name

        override fun paletteFor(systemPrefersDark: Boolean): Palette = palette
    }

    /**
     * Light or Dark, following the system.
     *
     * Only those two. Sepia and high contrast are choices a reader makes for their own reasons, and
     * no system preference says which of them anyone wants.
     */
    data object System : Theme {
        override val name: String = "System"

        override fun paletteFor(systemPrefersDark: Boolean): Palette =
            if (systemPrefersDark) Palettes.Dark else Palettes.Light
    }

    companion object {
        /** Every choice, in the order 5.5 lists them. */
        val all: List<Theme> = Palettes.all.map(::Fixed) + System

        /** The choice with [name], or null if no theme is called that. */
        fun named(name: String): Theme? = all.firstOrNull { it.name == name }
    }
}
