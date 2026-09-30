package com.appthere.drafts.design

/**
 * What the reader wants of animation, which is not always a yes or a no.
 *
 * `appthere-drafts.md` 10.2: "Honour `prefers-reduced-motion`." Honouring it means following it by
 * default, which a boolean cannot express -- `false` would mean both "I want animation" and "I have
 * not said", and the second of those should defer to the operating system while the first should
 * not.
 *
 * The same shape as [Theme], for the same reason: a system preference cannot be resolved when it is
 * chosen, only when it is drawn, because it can change while the document is open.
 *
 * The name is what goes to disk and is stable across versions for that reason.
 */
enum class MotionPreference {
    /** Animation as designed, whatever the system says. */
    Full,

    /** 10.2's "no cross-fades, no scroll animation, instant state changes", whatever the system says. */
    Reduced,

    /**
     * Whatever the operating system was told, which is the default.
     *
     * Someone who asked their system for less motion has already answered this question once, and
     * 10.2's "honour" is the promise that they do not have to answer it again here.
     */
    System,
    ;

    /** The durations in force, given what the system currently asks for. */
    fun motionFor(systemPrefersReduced: Boolean): Motion =
        when (this) {
            Full -> Motion.Standard
            Reduced -> Motion.Reduced
            System -> Motion.of(reducedMotion = systemPrefersReduced)
        }

    companion object {
        /** The choice with [name], or null if nothing is called that. */
        fun named(name: String): MotionPreference? = entries.firstOrNull { it.name == name }
    }
}
