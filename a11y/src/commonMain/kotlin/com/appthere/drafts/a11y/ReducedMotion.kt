package com.appthere.drafts.a11y

import androidx.compose.runtime.Composable

/**
 * Whether the operating system has been told to keep motion down.
 *
 * `appthere-drafts.md` 10.2: "Honour `prefers-reduced-motion`." 4.2 says the same of the one
 * animation this application has: "at reduced motion the switch is instantaneous."
 *
 * A composable, like `isSystemInDarkTheme()`, and for the same reason: on Android the answer lives
 * behind a `Context`, and there is nowhere else to get one. It also means a caller reads it during
 * composition and gets whatever the answer is now.
 *
 * This is the *system's* setting, not the reader's. A reader who has asked for reduced motion in
 * this application has said so in 5.5's controls; this is the separate promise that someone who
 * asked their operating system once should not have to ask again here.
 */
@Composable
expect fun systemPrefersReducedMotion(): Boolean
