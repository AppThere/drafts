package com.appthere.drafts.i18n

/**
 * User-facing strings.
 *
 * **Provisional.** `appthere-drafts.md` 11.1 requires these to live in resources, translatable and
 * adjustable for assistive technology; Compose Multiplatform's resource mechanism arrives with the
 * design system in Phase 3. Until then they live here rather than inline at the usage site, which
 * is the part that actually matters: a literal in a composable cannot be moved without touching
 * the UI, and there is no list of what needs translating.
 *
 * The custom detekt rule `drafts>HardcodedUserFacingString` is what keeps them out of composables.
 */
object Strings {
    const val WINDOW_TITLE = "Drafts"

    // The reader controls of `appthere-drafts.md` 5.5. Named for what they do to the reading
    // experience rather than for the property they set: "Text size", not "Base sp".
    const val READER_CONTROLS = "Reader"
    const val THEME = "Theme"
    const val TEXT_SIZE = "Text size"
    const val LINE_HEIGHT = "Line height"
    const val LETTER_SPACING = "Letter spacing"
    const val MEASURE = "Line length"
    const val BODY_WEIGHT = "Body weight"
    const val MOTION = "Motion"
    const val MOTION_FULL = "Full"
    const val MOTION_REDUCED = "Reduced"

    /**
     * The stepper buttons: a glyph to look at, and a word for anything that reads the screen
     * aloud. "Text size, increase" says what will happen; "Text size, +" says a punctuation mark.
     */
    const val LESS = "\u2212"
    const val MORE = "+"
    const val DECREASE = "decrease"
    const val INCREASE = "increase"

    // 5.1: the fonts ship under the SIL Open Font License, which requires the licence to travel
    // with them. "Licences" rather than "About": what this screen is for is the obligation.
    const val LICENCES = "Licences"
    const val CLOSE = "Close"
}
