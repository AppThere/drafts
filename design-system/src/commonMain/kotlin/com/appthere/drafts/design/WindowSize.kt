package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The width bands of `appthere-drafts.md` 6, which decide how much layout a window can carry. */
enum class WidthClass {
    /** < 600dp. "Single document, full bleed. Chrome auto-hides on typing." */
    Compact,

    /** 600-839dp. "Single document, centred column. Collapsible navigation rail." */
    Medium,

    /** >= 840dp. "Optional two-pane: document + outline, or two documents side by side." */
    Expanded,
}

/**
 * The height bands, which 6 is explicit about not forgetting.
 *
 * "Height classes matter too -- a phone in landscape is Compact-height, where auto-hiding chrome
 * is worth more than anywhere else."
 */
enum class HeightClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Which band a window falls in.
 *
 * The bands decide what *chrome* a window can afford. They deliberately do not decide the width of
 * the text: 5.3 does that continuously, and 6 says the layout "responds continuously rather than
 * snapping, with the content column doing the accommodating". A column that jumped at 840dp would
 * reflow a reader's paragraph for no reason they could see.
 */
@Immutable
data class WindowSize(
    val width: WidthClass,
    val height: HeightClass,
) {
    /** 6: a rail is worth its space from Medium up. */
    val hasRail: Boolean get() = width != WidthClass.Compact

    /** 6: two panes are an Expanded-width option, and only if there is height to use them. */
    val allowsTwoPanes: Boolean get() = width == WidthClass.Expanded && height != HeightClass.Compact

    /** 6: chrome auto-hides where it costs the most -- a compact window in either dimension. */
    val autoHidesChrome: Boolean
        get() = width == WidthClass.Compact || height == HeightClass.Compact

    companion object {
        val MediumWidth = 600.dp
        val ExpandedWidth = 840.dp
        val MediumHeight = 480.dp
        val ExpandedHeight = 900.dp

        fun of(
            width: Dp,
            height: Dp,
        ): WindowSize =
            WindowSize(
                width =
                    when {
                        width < MediumWidth -> WidthClass.Compact
                        width < ExpandedWidth -> WidthClass.Medium
                        else -> WidthClass.Expanded
                    },
                height =
                    when {
                        height < MediumHeight -> HeightClass.Compact
                        height < ExpandedHeight -> HeightClass.Medium
                        else -> HeightClass.Expanded
                    },
            )
    }
}
