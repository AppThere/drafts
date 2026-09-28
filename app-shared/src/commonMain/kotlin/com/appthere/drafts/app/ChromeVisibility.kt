package com.appthere.drafts.app

/**
 * When the chrome gets out of the way, per `appthere-drafts.md` 12.
 *
 * "**Chrome auto-hides.** On sustained typing, toolbars and rails fade out. Any pointer movement,
 * keypress of a modifier, or edge gesture brings them back."
 *
 * *Sustained* is the word doing the work. A single keystroke is not sustained typing -- somebody
 * correcting a word does not want the interface rearranging itself around them -- so the chrome
 * goes only once typing has been going on for a while without interruption.
 *
 * Coming back is not on a timer. 12 lists three ways: a pointer moves, a modifier is pressed, an
 * edge gesture. All three are the reader reaching for something. Stopping typing is not on the
 * list and is deliberately not treated as one: a writer who pauses to think has not asked for the
 * furniture back, and restoring it would be the interface fidgeting at exactly the moment they
 * were trying to concentrate.
 *
 * No clock. The caller says what time it is, which is what makes "sustained" testable at an
 * instant rather than by typing for two seconds.
 */
class ChromeVisibility(
    private val sustainedAfterMillis: Long = SUSTAINED_AFTER_MILLIS,
) {
    private var typingSince: Long? = null

    /** Records an edit. The first one after being roused starts the clock. */
    fun typed(now: Long) {
        if (typingSince == null) typingSince = now
    }

    /**
     * The reader reached for something: a pointer moved, a modifier went down, an edge was
     * touched. The chrome comes back and stays until typing is sustained again.
     */
    fun roused() {
        typingSince = null
    }

    /** Whether the chrome should be out of the way at [now]. */
    fun isHidden(now: Long): Boolean = typingSince?.let { now - it >= sustainedAfterMillis } ?: false

    /**
     * When [isHidden] would next become true, so a caller can sleep until then rather than poll.
     * Null when nothing is pending.
     */
    fun hidesAt(): Long? = typingSince?.plus(sustainedAfterMillis)

    companion object {
        /**
         * How long typing has to go on before it counts as sustained.
         *
         * 12 does not give a number. Long enough that fixing a typo never moves anything, short
         * enough that a sentence written straight through clears the page -- about a line of
         * unhesitating prose.
         */
        const val SUSTAINED_AFTER_MILLIS = 1_500L
    }
}
