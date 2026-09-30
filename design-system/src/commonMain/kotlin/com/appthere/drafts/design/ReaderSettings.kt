package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.TextUnit

/**
 * What the reader has chosen, as `appthere-drafts.md` 5.5 lists it.
 *
 * Held together in one value because they are read together and persisted together -- 5.5 says
 * "Exposed in settings, persisted per document type". Defaults are the spec's defaults, so a fresh
 * document is the document the spec describes rather than whatever the code happened to start at.
 */
@Immutable
data class ReaderSettings(
    /** 5.5: "Theme: light, dark, sepia, high contrast, system". Resolved to a palette by [DraftsTheme]. */
    val theme: Theme = Theme.Fixed(Palettes.Light),
    val base: TextUnit = Prose.DefaultBase,
    /**
     * 5.5: "Line height multiplier (1.3-2.0) -- dyslexia support", which is the *body* line height
     * and not a factor applied on top of it. 5.2 sets body at 1.6, comfortably inside that range,
     * so the default is the scale's own value and the control moves away from it in both
     * directions. Every other role moves with it in proportion -- see `proseStyleOf`.
     */
    val lineHeight: Float = Prose.Body.lineHeight,
    /** 5.5: 0 to +0.08em, likewise. */
    val letterSpacing: Float = 0f,
    /**
     * 5.5: "Paragraph spacing", as the space after a body paragraph in em.
     *
     * 5.2 sets that at 0.75em, so that is the default, and -- as with [lineHeight] -- every other
     * role's space moves with it in proportion, so a heading keeps more air than the paragraphs
     * around it at any setting. The spec gives no range. The floor is above zero because 5.2 says
     * "paragraphs are separated by space, not first-line indent": at zero there would be nothing
     * left to separate them.
     */
    val paragraphSpacing: Float = Prose.Body.spaceAfter,
    /** 5.5: 55-85 characters. */
    val characters: Float = Measure.MEASURE_EM * 2,
    /** 5.5: 300-500 for body, for low-vision support. */
    val bodyWeight: Int = DEFAULT_BODY_WEIGHT,
    /**
     * 10.2's `prefers-reduced-motion`, following the system unless the reader says otherwise.
     *
     * The default is [MotionPreference.System] rather than "full": someone who asked their
     * operating system for less motion has answered this once already.
     */
    val motion: MotionPreference = MotionPreference.System,
    /**
     * 12: "**Typewriter scrolling** as an option: keep the caret at a fixed vertical position."
     *
     * Off by default. It moves the document under the reader as they type, which is what some
     * people want and a reason others give up on an editor.
     */
    val typewriterScrolling: Boolean = false,
    /** 12: "**Focus mode** as an option: dim all blocks except the current one". */
    val focusMode: FocusMode = FocusMode.Off,
    /**
     * 12: "**Chrome auto-hides.** On sustained typing, toolbars and rails fade out."
     *
     * On by default, because the spec states it as what the interface does rather than as an
     * option. It is a setting at all because "the chrome disappeared" is a thing a reader may need
     * to be able to stop happening -- particularly one navigating by a screen reader, for whom a
     * control that has faded is a control that has moved.
     */
    val autoHideChrome: Boolean = true,
) {
    /** The same settings with every control held to the range 5.5 allows. */
    fun clamped(): ReaderSettings =
        copy(
            base = Prose.clampBase(base),
            lineHeight = lineHeight.coerceIn(MINIMUM_LINE_HEIGHT, MAXIMUM_LINE_HEIGHT),
            letterSpacing = letterSpacing.coerceIn(0f, MAX_LETTER_SPACING),
            paragraphSpacing = paragraphSpacing.coerceIn(MINIMUM_PARAGRAPH_SPACING, MAXIMUM_PARAGRAPH_SPACING),
            characters = characters.coerceIn(Measure.MINIMUM_CHARACTERS, Measure.MAXIMUM_CHARACTERS),
            bodyWeight = bodyWeight.coerceIn(MIN_BODY_WEIGHT, MAX_BODY_WEIGHT),
        )

    companion object {
        const val MINIMUM_LINE_HEIGHT = 1.3f
        const val MAXIMUM_LINE_HEIGHT = 2.0f
        const val MAX_LETTER_SPACING = 0.08f
        const val MINIMUM_PARAGRAPH_SPACING = 0.5f
        const val MAXIMUM_PARAGRAPH_SPACING = 1.5f
        const val MIN_BODY_WEIGHT = 300
        const val MAX_BODY_WEIGHT = 500
        const val DEFAULT_BODY_WEIGHT = 400
    }
}
