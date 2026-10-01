package com.appthere.drafts.platform.windows

import androidx.compose.runtime.Composable
import com.appthere.drafts.design.Fold

/**
 * The hinge across this window, if the platform reports one.
 *
 * `appthere-drafts.md` 6: "Foldables: use Jetpack WindowManager's `FoldingFeature` to avoid
 * rendering text across a hinge."
 *
 * A composable, like `systemPrefersReducedMotion()`, and for two reasons. On Android the answer
 * lives behind a `Context`, which only a composition has. And it *changes*: a reader folding the
 * device halfway while a paragraph is on screen is the case 6 is written for, and a value read once
 * at startup would answer for the posture the device happened to be in then.
 *
 * Null means no hinge in this window -- a phone, a desktop, a tablet, or a foldable whose window
 * does not span the fold. Null rather than a `Fold` with `isSeparating = false`, because "there is
 * no fold" and "there is a fold and it is flat" are different facts and only the second is worth
 * reporting.
 */
@Composable
expect fun currentFold(): Fold?
