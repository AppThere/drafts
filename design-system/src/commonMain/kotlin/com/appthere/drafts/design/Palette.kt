package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours a document is set in.
 *
 * Deliberately small. This is the palette of a *reading surface*, not a component library: a
 * ground, the ink on it, a quieter ink for the markup the preview shows rather than hides, one
 * accent, and the tint of a screenplay line being edited. Everything Phase 2 needed was one of the
 * first four, and a palette that only contains what is used is one that can be checked for contrast
 * exhaustively -- which `PaletteTest` does.
 *
 * [muted] is held to the body threshold rather than the affordance one. It carries code fences,
 * list bullets and the quote rule: characters the author typed or can edit, which makes them text
 * that has to be readable, not decoration that merely has to be noticed.
 */
@Immutable
data class Palette(
    /**
     * A stable identifier, never shown to anybody.
     *
     * 11.1 puts user-facing strings in resources, which means they get translated -- and this value
     * is the key a reader's chosen theme is saved under. A name that is both would lose every saved
     * theme the first time the application was translated, so the two are separate and this is the
     * half that never changes.
     */
    val id: String,
    val background: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    /**
     * The ground under the screenplay line being edited.
     *
     * That line is set full width, as plain text, until the caret leaves it and it is laid out in its
     * role again (`appthere-drafts.md` 4.5, as `divergences.md` records it). The tint is what says
     * so: this line is being written, not yet placed. Every text colour clears the same floor on it
     * as on [background].
     */
    val editing: Color,
    /** The floor every text colour on this palette must clear against [background]. */
    val minimumContrast: Double = Contrast.BODY,
)

/**
 * The four themes `IMPLEMENTATION-PLAN.md` asks for in Phase 3.
 *
 * Chosen warm rather than pure: a document on `#FFFFFF` under a lamp is a light source, and these
 * are surfaces meant to be looked at for hours. The numbers are all verified against
 * `appthere-drafts.md` 10.2 in `PaletteTest`, so a future adjustment that looks nicer and reads
 * worse fails the build rather than shipping.
 */
object Palettes {
    /** Paper under warm light. */
    val Light =
        Palette(
            id = "light",
            background = Color(0xFFFDFCFA),
            ink = Color(0xFF1B1B1B),
            muted = Color(0xFF5C5C5C),
            accent = Color(0xFF1A4FA0),
            editing = Color(0xFFEAEFF7),
        )

    /** Not black: a true black ground makes light text bloom, which is worse to read, not better. */
    val Dark =
        Palette(
            id = "dark",
            background = Color(0xFF14161A),
            ink = Color(0xFFE8E6E3),
            muted = Color(0xFF9BA0A6),
            accent = Color(0xFF9CC2FF),
            editing = Color(0xFF1F2530),
        )

    /** The long-session palette: lower blue, lower contrast between ink and ground than Light. */
    val Sepia =
        Palette(
            id = "sepia",
            background = Color(0xFFF4ECD8),
            ink = Color(0xFF2E2619),
            muted = Color(0xFF5A4B36),
            accent = Color(0xFF7A431A),
            editing = Color(0xFFEADFC4),
        )

    /** 10.2: "The high-contrast theme targets 7:1." Everything here clears it, including muted. */
    val HighContrast =
        Palette(
            id = "high-contrast",
            background = Color(0xFF000000),
            ink = Color(0xFFFFFFFF),
            muted = Color(0xFFD6D6D6),
            accent = Color(0xFFFFD400),
            editing = Color(0xFF262626),
            minimumContrast = Contrast.HIGH,
        )

    /** Every palette, so a test can check them all rather than the ones someone remembered. */
    val all = listOf(Light, Dark, Sepia, HighContrast)
}
