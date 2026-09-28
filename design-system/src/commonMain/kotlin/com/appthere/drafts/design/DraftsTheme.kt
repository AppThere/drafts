package com.appthere.drafts.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit

/**
 * The reader's settings, everywhere below [DraftsTheme].
 *
 * `staticCompositionLocalOf` rather than the dynamic kind: these change when someone opens settings
 * and not otherwise, and the static form does not track reads, so a change re-composes the subtree
 * once instead of paying to know precisely who cared.
 */
val LocalReaderSettings: ProvidableCompositionLocal<ReaderSettings> =
    staticCompositionLocalOf { ReaderSettings() }

/**
 * The palette in force. Split out from the settings because almost everything reads only this, and
 * because the settings hold a [Theme] choice that only becomes a palette here.
 */
val LocalPalette: ProvidableCompositionLocal<Palette> = staticCompositionLocalOf { Palettes.Light }

/** Durations, already resolved against `prefers-reduced-motion`. */
val LocalMotion: ProvidableCompositionLocal<Motion> = staticCompositionLocalOf { Motion.Standard }

/**
 * Provides the design system to everything inside it.
 *
 * Deliberately not a `MaterialTheme`. This app's surface is a document, and the parts of Material
 * that matter here are the ones Phase 3 replaces: the type scale, the colours and the measure.
 * Components that want Material can still be wrapped in it; nothing here depends on it.
 */
@Composable
fun DraftsTheme(
    settings: ReaderSettings = ReaderSettings(),
    content: @Composable () -> Unit,
) {
    val held = settings.clamped()

    // Read on every composition rather than once, so a document open when the system switches to
    // dark in the evening follows it -- which is what choosing "system" asked for.
    val palette = held.theme.paletteFor(systemPrefersDark = isSystemInDarkTheme())

    CompositionLocalProvider(
        LocalReaderSettings provides held,
        LocalPalette provides palette,
        LocalMotion provides Motion.of(held.reducedMotion),
        content = content,
    )
}
