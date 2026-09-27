package com.appthere.drafts.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG contrast ratios, computed rather than judged by eye.
 *
 * `appthere-drafts.md` 10.2 sets the thresholds -- "4.5:1 minimum for body text, 3:1 for large text
 * and UI affordances, in every theme. The high-contrast theme targets 7:1" -- and the Phase 3
 * acceptance criteria say they are "computed and tested, not eyeballed". This file is what makes
 * that possible: a palette that fails is a failing test, not a thing someone notices later on a
 * different monitor.
 *
 * The formula is WCAG 2.x: linearise each channel out of sRGB, weight them for luminance, and
 * compare the two results with the 0.05 term that keeps black from dividing by zero.
 */
object Contrast {
    /** Body text, and anything else a reader has to read at length. */
    const val BODY = 4.5

    /** Large text and UI affordances -- 18.66sp bold or 24sp regular and up. */
    const val LARGE = 3.0

    /** What the high-contrast theme is for. */
    const val HIGH = 7.0

    /**
     * The contrast ratio between two opaque colours, from 1.0 to 21.0.
     *
     * Order does not matter: the lighter of the two is always the numerator.
     */
    fun ratio(
        one: Color,
        other: Color,
    ): Double {
        val a = luminance(one)
        val b = luminance(other)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)

        return (lighter + OFFSET) / (darker + OFFSET)
    }

    /** WCAG relative luminance: sRGB channels linearised, then weighted for perceived brightness. */
    fun luminance(color: Color): Double =
        RED_WEIGHT * linear(color.red) + GREEN_WEIGHT * linear(color.green) + BLUE_WEIGHT * linear(color.blue)

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= LINEAR_LIMIT) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
    }

    private const val OFFSET = 0.05

    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722

    private const val LINEAR_LIMIT = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA = 2.4
}
