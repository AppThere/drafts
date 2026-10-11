package com.appthere.drafts.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * How the interface stands off the desk: separation by tone and depth rather than by ruled lines.
 *
 * Three layers, and only three. The desk is the window's ground. The page rests on it. Panels,
 * sheets and the chrome's buttons float over the page. A fourth layer would be a hierarchy nobody
 * can read at a glance, which is what the layers are for.
 */

/** How far a surface stands off what is under it. */
enum class Elevation(
    val depth: Dp,
) {
    /** The page, resting on the desk. */
    Page(2.dp),

    /** Panels, sheets and the chrome's buttons, over the page. */
    Floating(6.dp),
}

/** The corners the interface uses: round for what is pressed, large for what holds things. */
object Corners {
    /** Buttons, options, the status: fully rounded, so a control never looks like a container. */
    val Pill: Shape = RoundedCornerShape(percent = 50)

    /** The page and every panel. */
    val Container: Shape = RoundedCornerShape(28.dp)

    /** A sheet along the bottom of a phone: rounded where it meets the page, square at the edge. */
    val Sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/**
 * Fills this with [colour] in [shape], raised to [elevation].
 *
 * Raised by a shadow, unless the palette says a shadow will not show -- on black there is nothing
 * darker to draw one with -- in which case by the palette's outline instead. Either way the edge is
 * there; which one draws it is the palette's decision, not each caller's.
 */
@Composable
fun Modifier.raised(
    colour: Color,
    shape: Shape,
    elevation: Elevation,
): Modifier {
    val outline = LocalPalette.current.outline
    return if (outline == null) {
        shadow(elevation.depth, shape).background(colour, shape)
    } else {
        background(colour, shape).border(outlineWidth, outline, shape)
    }
}

/**
 * The page: full bleed on a phone, a sheet resting on the desk anywhere wider.
 *
 * 6 asks for Compact windows to be "full bleed", and on a phone the margin a sheet needs is width
 * taken from the measure. Wider than that there is room to spare, and spending a little of it on
 * the desk is what lets the page read as the thing in the window rather than as the window.
 */
@Composable
fun Modifier.pageSurface(): Modifier {
    val palette = LocalPalette.current
    return if (LocalWindowSize.current.width == WidthClass.Compact) {
        background(palette.background)
    } else {
        padding(pageMargin).raised(palette.background, Corners.Container, Elevation.Page)
    }
}

/**
 * What every panel is drawn on: the panel tone, raised over the page.
 *
 * Rounded all round where it floats beside the text, and only along its top edge where it is 6's
 * sheet on a phone -- there it meets the bottom of the screen, and a rounded corner against the
 * glass would leave a sliver of page showing beneath it.
 */
@Composable
fun Modifier.panelSurface(): Modifier {
    val shape = if (LocalWindowSize.current.width == WidthClass.Compact) Corners.Sheet else Corners.Container
    return raised(LocalPalette.current.panel, shape, Elevation.Floating)
}

private val outlineWidth = 1.dp

/** The desk showing round the page, where the window is wide enough to spare it. */
private val pageMargin = 12.dp
