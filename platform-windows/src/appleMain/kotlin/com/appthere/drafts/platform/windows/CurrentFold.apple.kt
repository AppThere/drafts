package com.appthere.drafts.platform.windows

import androidx.compose.runtime.Composable
import com.appthere.drafts.design.Fold

/**
 * No Apple device folds, and UIKit has no API that would report one if it did.
 *
 * The expect/actual exists so that the shared layout can ask the question unconditionally. An
 * `if (Platform.isAndroid)` in `:app-shared` would be the alternative, and it is the thing the
 * module boundary is for.
 */
@Composable
actual fun currentFold(): Fold? = null
