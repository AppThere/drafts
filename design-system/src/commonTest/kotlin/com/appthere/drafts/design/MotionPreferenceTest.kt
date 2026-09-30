package com.appthere.drafts.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 10.2: "Honour `prefers-reduced-motion`."
 *
 * Honouring it is the [MotionPreference.System] case, and it is the one a boolean could not
 * express: `false` would have meant both "I want animation" and "I have not said", and only the
 * second of those should defer to the operating system.
 */
class MotionPreferenceTest {
    @Test
    fun `following the system means following it`() {
        // The whole point, and the case that passes silently if `System` is mapped to a constant.
        assertEquals(Motion.Reduced, MotionPreference.System.motionFor(systemPrefersReduced = true))
        assertEquals(Motion.Standard, MotionPreference.System.motionFor(systemPrefersReduced = false))
    }

    @Test
    fun `asking for less overrides a system that asks for more`() {
        // A reader may want less motion in a document than the rest of their desktop.
        assertEquals(Motion.Reduced, MotionPreference.Reduced.motionFor(systemPrefersReduced = false))
    }

    @Test
    fun `asking for full motion overrides a system that asks for less`() {
        // And the other way, which is the harder direction to get right: someone who turned the
        // system setting on for other applications can still say they want it here.
        assertEquals(Motion.Standard, MotionPreference.Full.motionFor(systemPrefersReduced = true))
    }

    @Test
    fun `reduced motion is instant everywhere`() {
        // 10.2: "no cross-fades, no scroll animation, instant state changes." Zeroing the durations
        // rather than branching per call site is what makes that true without anything to forget.
        assertTrue(MotionPreference.Reduced.motionFor(systemPrefersReduced = false).isInstant)
    }

    @Test
    fun `the default follows the system`() {
        assertEquals(MotionPreference.System, ReaderSettings().motion)
    }

    @Test
    fun `a choice survives the trip to disk`() {
        // The name is the key settings are saved under, so it has to be stable and it has to come
        // back. A reader who asked for reduced motion should not find it restored on next launch.
        MotionPreference.entries.forEach {
            assertEquals(it, MotionPreference.named(it.name), "for ${it.name}")
        }
    }

    @Test
    fun `an unknown name is unknown rather than a guess`() {
        assertNull(MotionPreference.named("Somewhat"))
    }
}
