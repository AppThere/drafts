package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours a document is set in, and the colours of the desk it rests on.
 *
 * Two halves. The page: a ground, the ink on it, a quieter ink for the markup the preview shows
 * rather than hides, one accent, and the tint of a line being edited. Then the interface around the
 * page, in tones rather than lines: the [desk] the page sits on, the [panel] that panels and the
 * chrome's buttons are raised on, the [control] fill of a button, and the fill of a status that
 * needs [attention].
 *
 * There is no tonal "selected" fill. One was tried, and a tone gentle enough to sit beside the
 * control fill was 1.03:1 from it in the light theme -- a chosen option nobody could tell was
 * chosen. What is chosen is filled with the [accent] and lettered in the [background], a pair the
 * accent's own test already holds to the floor.
 *
 * Every colour is one something draws with, and every text colour is checked against every fill it
 * can sit on -- which `PaletteTest` does exhaustively. A palette that only contains what is used is
 * one that can be checked like that.
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
    /**
     * The ground around the page: what shows at the window's edges and beside the outline.
     *
     * A step away from [background] in tone, so the page reads as a sheet laid on something rather
     * than as the whole window. Text sits on it too -- the outline, when it is a pane -- so it is
     * held to the same floor.
     */
    val desk: Color,
    /** Panels, sheets, and the pill the chrome's buttons sit in: raised over the page. */
    val panel: Color,
    /** A button's fill, on a [panel]. Every text colour clears the floor on it. */
    val control: Color,
    /** The fill of the status when something has happened to the file (8.4), with [onAttention]. */
    val attention: Color,
    val onAttention: Color,
    /**
     * Drawn around raised surfaces in place of a shadow, or null where the shadow is enough.
     *
     * A shadow is a darker edge, and on a black ground there is nothing darker: in the high-contrast
     * theme a panel with only a shadow would have no edge at all.
     */
    val outline: Color? = null,
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
            desk = Color(0xFFEFECE6),
            panel = Color(0xFFF6F4F0),
            control = Color(0xFFE8E5DE),
            attention = Color(0xFFFFDCC2),
            onAttention = Color(0xFF2F1500),
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
            // Darker than the page rather than lighter: in a dark theme what is raised is lighter,
            // and the page is raised off the desk.
            desk = Color(0xFF0B0C0F),
            panel = Color(0xFF23262D),
            control = Color(0xFF2E323A),
            attention = Color(0xFF5A3510),
            onAttention = Color(0xFFFFDCC2),
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
            desk = Color(0xFFE6DCC3),
            panel = Color(0xFFF8F2E4),
            control = Color(0xFFE9DFC6),
            attention = Color(0xFFEBCFC4),
            onAttention = Color(0xFF3D140A),
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
            desk = Color(0xFF000000),
            panel = Color(0xFF000000),
            control = Color(0xFF262626),
            attention = Color(0xFF3D1F00),
            onAttention = Color(0xFFFFFFFF),
            outline = Color(0xFFFFFFFF),
            minimumContrast = Contrast.HIGH,
        )

    /** Every palette, so a test can check them all rather than the ones someone remembered. */
    val all = listOf(Light, Dark, Sepia, HighContrast)
}
