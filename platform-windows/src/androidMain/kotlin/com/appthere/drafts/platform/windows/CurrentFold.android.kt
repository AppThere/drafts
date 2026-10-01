package com.appthere.drafts.platform.windows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.appthere.drafts.design.Fold
import com.appthere.drafts.design.FoldAxis

/**
 * Android's answer, from Jetpack WindowManager -- the library 6 names.
 *
 * The flow is per-context and emits again every time the posture changes, so folding the device
 * while a document is open recomposes the layout around the new hinge. It is created once per
 * context rather than per composition: `windowLayoutInfo` registers a listener with the platform,
 * and building a fresh flow on every recomposition would register and unregister one per frame.
 *
 * The initial value is null -- no hinge known yet -- rather than "no hinge". The first emission
 * arrives a frame or two after the window is attached, and a layout that had assumed no fold for
 * those frames would show the text across the hinge and then move it, which is worse than showing
 * it in the right place slightly late.
 *
 * `firstOrNull` is deliberate. A window can report several display features, and a device with two
 * hinges exists; this handles the single-hinge case the spec describes and takes the first of
 * anything stranger rather than pretending to handle it.
 */
@Composable
actual fun currentFold(): Fold? {
    val context = LocalContext.current
    val density = LocalDensity.current

    val layout by remember(context) {
        WindowInfoTracker.getOrCreate(context).windowLayoutInfo(context)
    }.collectAsState(initial = null)

    val feature =
        layout
            ?.displayFeatures
            ?.filterIsInstance<FoldingFeature>()
            ?.firstOrNull()
            ?: return null

    // WindowManager reports bounds in the window's own pixels. Dp, because 6's decision is about
    // layout and everything it is compared against -- the window width, 5.3's measure -- is in Dp.
    return with(density) {
        // VERTICAL is WindowManager's name for a feature taller than it is wide, which is a hinge
        // running top to bottom. That is the one that cuts across a line of text.
        val vertical = feature.orientation == FoldingFeature.Orientation.VERTICAL

        Fold(
            axis = if (vertical) FoldAxis.Vertical else FoldAxis.Horizontal,
            start = (if (vertical) feature.bounds.left else feature.bounds.top).toDp(),
            end = (if (vertical) feature.bounds.right else feature.bounds.bottom).toDp(),
            isSeparating = feature.isSeparating,
        )
    }
}
