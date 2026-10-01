package com.appthere.drafts.platform.windows

import androidx.compose.runtime.Composable
import com.appthere.drafts.design.Fold

/**
 * Desktop windows do not fold.
 *
 * A desktop window on a foldable would, but there is no such platform here: the desktop targets are
 * Linux, macOS and Windows on hardware with one flat display each. Null rather than an attempt at
 * detection, because an attempt would be untestable and would answer null anyway.
 */
@Composable
actual fun currentFold(): Fold? = null
