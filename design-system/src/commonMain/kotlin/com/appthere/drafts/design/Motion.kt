package com.appthere.drafts.design

import androidx.compose.runtime.Immutable

/**
 * How long things take, and what happens when the reader has asked for less of it.
 *
 * `appthere-drafts.md` 4.2 sets the one duration this app really has: "Cross-fade inline decoration
 * over 120ms with no layout animation... Respect `prefers-reduced-motion`: at reduced motion the
 * switch is instantaneous." 10.2 widens that to everything: "no cross-fades, no scroll animation,
 * instant state changes."
 *
 * Reduced motion is expressed by zeroing the durations rather than by branching at each call site.
 * A branch is a thing to forget; a zero-length animation is instant everywhere at once, and the
 * code that reads the token does not have to know which mode it is in.
 */
@Immutable
data class Motion(
    /** 4.2: the reveal/preview cross-fade. Inline decoration only -- never layout. */
    val revealMillis: Int,
    /** Chrome appearing and disappearing, which 6 does on typing in a compact window. */
    val chromeMillis: Int,
) {
    val isInstant: Boolean get() = revealMillis == 0 && chromeMillis == 0

    companion object {
        val Standard = Motion(revealMillis = 120, chromeMillis = 200)

        /** Everything instant, for `prefers-reduced-motion`. */
        val Reduced = Motion(revealMillis = 0, chromeMillis = 0)

        fun of(reducedMotion: Boolean) = if (reducedMotion) Reduced else Standard
    }
}
