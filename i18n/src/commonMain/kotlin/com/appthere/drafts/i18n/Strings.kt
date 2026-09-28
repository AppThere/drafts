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
    const val PARAGRAPH_SPACING = "Paragraph spacing"
    const val BODY_WEIGHT = "Body weight"
    const val MOTION = "Motion"
    const val MOTION_FULL = "Full"
    const val MOTION_REDUCED = "Reduced"

    /** A settings file that could not be written: what it means for the reader, not the disk. */
    const val SETTINGS_NOT_SAVED =
        "These settings could not be kept. They apply until this window closes, " +
            "and will need choosing again next time."

    /**
     * 12's two reading options, named for what they do rather than for what they are called.
     *
     * "Typewriter scrolling" is the term of art and means nothing to someone meeting it for the
     * first time, so the choice is spelled out: the page moves, or it stays.
     */
    const val TYPEWRITER = "While typing"
    const val TYPEWRITER_ON = "Keep the line centred"
    const val TYPEWRITER_OFF = "Leave the page still"

    const val FOCUS = "Focus"
    const val FOCUS_OFF = "Whole document"
    const val FOCUS_BLOCK = "Current paragraph"

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

    /**
     * The five states of `appthere-drafts.md` 8.4, as the "short label" it asks for.
     *
     * Written from the reader's side. `conflicted` is a state name; "Changed on disk" is what
     * happened. The one with no words is `clean` -- there is nothing to tell someone about a
     * document that matches its file.
     */
    const val DOCUMENT_STATE = "Document"
    const val STATE_CLEAN = "Saved"
    const val STATE_DIRTY = "Unsaved"
    const val STATE_CONFLICTED = "Changed on disk"
    const val STATE_ORPHANED = "File missing"
    const val STATE_READ_ONLY = "Read-only"

    const val OPENING = "Opening\u2026"
    const val COULD_NOT_OPEN = "Could not open this document"

    /**
     * 8.3's banner, in its own words.
     *
     * "*Unsaved changes from your last session have been restored.* [ Compare ] [ Discard ]"
     *
     * `Compare` is not offered: it needs a diff view, which does not exist, and the same omission
     * is already recorded for 8.2's "Show differences". `KEEP` is not in the spec and is here
     * because the banner has to be dismissible -- an unobtrusive banner that cannot be got rid of
     * stops being unobtrusive by the second document.
     */
    const val RESTORED = "Unsaved changes from your last session have been restored."
    const val DISCARD = "Discard"
    const val KEEP = "Keep"

    /**
     * 8.2's refusal, in its own words.
     *
     * "*This file has changed on disk since you opened it.* [ Save a copy... ] [ Reload and lose my
     * changes ] [ Show differences ] [ Cancel ]"
     *
     * Two of those four are missing here on purpose. "Save a copy" needs a platform save dialog and
     * "Show differences" needs a diff view; neither exists yet, and a button that does nothing is
     * worse than one that is not offered. The two that are here are the two that work, and the
     * refusal itself -- the part that protects the file -- does not depend on any of them.
     *
     * `RELOAD` keeps the spec's full phrasing rather than shortening to "Reload". What is being
     * lost is the whole point of the sentence, and a reader clicking a button labelled "Reload"
     * would not have been told.
     */
    const val CONFLICT = "This file has changed on disk since you opened it."
    const val RELOAD = "Reload and lose my changes"
    const val CANCEL = "Cancel"
}
