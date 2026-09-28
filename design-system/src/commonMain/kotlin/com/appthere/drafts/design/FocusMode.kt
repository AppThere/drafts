package com.appthere.drafts.design

/**
 * How much of the document is kept bright, per `appthere-drafts.md` 12.
 *
 * "**Focus mode** as an option: dim all blocks except the current one, or the current sentence."
 *
 * Dimming and not hiding. A reader needs to see that there is more document above and below -- how
 * far through a chapter they are is information -- and a mode that removed it would be a different
 * feature with a different name.
 */
enum class FocusMode {
    /** Everything at full contrast. The default, because focus mode is offered rather than imposed. */
    Off,

    /** Everything but the block holding the caret is dimmed. */
    Block,

    // 12 also offers "the current sentence", which is deliberately absent. Finding a sentence
    // boundary is a locale question, not a punctuation one: a full stop ends "end." and does not
    // end "Dr. Smith", Japanese uses `。`, and Greek asks a question with `;`. 11.1 requires this
    // application to work in those languages, so a naive split would dim the wrong half of a
    // sentence for most of the world. It belongs with real segmentation, not before it.
}
