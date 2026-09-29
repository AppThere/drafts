package com.appthere.drafts.i18n

/**
 * User-facing strings.
 *
 * **Provisional.** `appthere-drafts.md` 11.1 requires these to live in resources, translatable and
 * adjustable for assistive technology. Compose Multiplatform's resource mechanism has been in the
 * build since Phase 3 and these have not moved to it yet; `divergences.md` puts that before the
 * Phase 5 i18n audit. Until then they live here rather than inline at the usage site, which
 * is the part that actually matters: a literal in a composable cannot be moved without touching
 * the UI, and there is no list of what needs translating.
 *
 * The custom detekt rule `drafts>HardcodedUserFacingString` is what keeps them out of composables.
 */
object Strings {
    const val WINDOW_TITLE = "Drafts"

    /** 7.4: the name a new document has until its first save gives it one. */
    const val UNTITLED = "Untitled"

    /**
     * 10.1's names for blocks, as a screen reader announces them. Named for what a writer calls
     * them, not for the Markdown construct: "Section break", not "thematic break".
     */
    const val BLOCK_PARAGRAPH = "Paragraph"
    const val BLOCK_HEADING_LEVEL = "Heading level"
    const val BLOCK_QUOTE = "Block quote"
    const val BLOCK_CODE = "Code block"
    const val BLOCK_BULLETED_LIST = "Bulleted list"
    const val BLOCK_NUMBERED_LIST = "Numbered list"
    const val BLOCK_DEFINITION_LIST = "Definition list"
    const val BLOCK_TABLE = "Table"
    const val BLOCK_SECTION_BREAK = "Section break"
    const val BLOCK_FIGURE = "Figure"
    const val BLOCK_RAW = "HTML"
    const val BLOCK_LINK_REFERENCE = "Link reference"
    const val BLOCKS_JOINED = "Joined with the block above."

    /**
     * 10.2's shortcut map: the keys' names, and what each shortcut does, said as the reader would
     * say it. Ctrl stands for ⌘ as well on a Mac, and the list says so once rather than every time.
     */
    const val KEY_CTRL = "Ctrl"
    const val KEY_SHIFT = "Shift"
    const val KEY_ESCAPE = "Esc"
    const val KEY_BACKSPACE = "Backspace"
    const val KEY_DELETE = "Delete"
    const val SHORTCUT_SELECT_ALL = "Select everything"
    const val SHORTCUT_COPY = "Copy"
    const val SHORTCUT_CUT = "Cut"
    const val SHORTCUT_PASTE = "Paste"
    const val SHORTCUT_UNDO = "Undo"
    const val SHORTCUT_REDO = "Redo"
    const val SHORTCUT_DELETE_SELECTION = "Delete what is selected"
    const val SHORTCUT_SAVE = "Save"
    const val SHORTCUT_SAVE_AS = "Save As, or save a copy somewhere else"
    const val SHORTCUT_READER_CONTROLS = "Show or hide the reader controls"
    const val SHORTCUT_LIST = "Show or hide this list"
    const val SHORTCUT_CLOSE = "Close what is open"
    const val SHORTCUT_FULL_SCREEN = "Enter or leave full screen (on a Mac, also Ctrl+\u2318+F)"
    const val KEYBOARD_SHORTCUTS = "Keyboard shortcuts"
    const val SHORTCUTS_WRITING = "Writing"
    const val SHORTCUTS_DOCUMENT = "The document and the window"
    const val SHORTCUTS_ON_A_MAC = "On a Mac, \u2318 works wherever Ctrl is shown."

    /** 7.4's choice of kind for an untitled document, named as a writer would name them. */
    const val KIND = "Kind"
    const val KIND_MARKDOWN = "Markdown"
    const val KIND_FOUNTAIN = "Fountain"

    /** 7.4's launcher entry points, where a platform shows them from inside the application. */
    const val NEW_MARKDOWN = "New Markdown document"
    const val NEW_FOUNTAIN = "New Fountain screenplay"

    /** 7.4's *Save As*: the dialog's title, and the words when a save does not happen. */
    const val SAVE_AS = "Save As"
    const val COULD_NOT_SAVE =
        "This document could not be saved. Your words are safe in this window, and will be here " +
            "next time too."

    // The reader controls of `appthere-drafts.md` 5.5. Named for what they do to the reading
    // experience rather than for the property they set: "Text size", not "Base sp".
    const val READER_CONTROLS = "Reader"
    const val OPEN_READER_CONTROLS = "Reader controls"
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
    const val STATE_UNTITLED = "Not saved yet"
    const val STATE_CLEAN = "Saved"
    const val STATE_DIRTY = "Unsaved"
    const val STATE_CONFLICTED = "Changed on disk"
    const val STATE_ORPHANED = "File missing"
    const val STATE_READ_ONLY = "Read-only"

    const val OPENING = "Opening\u2026"

    /** A document that could not be opened, in words about the reader's situation (7.3). */
    const val COULD_NOT_READ =
        "This document could not be read. It may be somewhere Drafts cannot reach just now, such as " +
            "a drive that is not connected."
    const val FILE_GONE_NOTHING_KEPT =
        "This document's file is no longer there, and no copy of its words was kept here."

    /** 7.3's banner for a document whose file vanished, and the answer it offers. */
    const val FILE_GONE =
        "The file this document was saved in is no longer there. Its words are safe here. Choose a " +
            "place to save them again."
    const val SAVE_AS_CHOICE = "Save As\u2026"

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
    const val SAVE_COPY = "Save a copy\u2026"
    const val RELOAD = "Reload and lose my changes"

    /** What "Save a copy" calls the copy, so the dialog does not open on the file being kept. */
    const val MY_VERSION = "my version"
    const val CANCEL = "Cancel"
}
