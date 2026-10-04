package com.appthere.drafts.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.appthere.drafts.design.resources.Res
import com.appthere.drafts.design.resources.atkinson_mono
import com.appthere.drafts.design.resources.atkinson_mono_italic
import com.appthere.drafts.design.resources.atkinson_next
import com.appthere.drafts.design.resources.atkinson_next_italic
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.Font as resourceFont

// The typefaces, and what happens when they run out of glyphs.
//
// `appthere-drafts.md` 5.1 chooses Atkinson Hyperlegible Next and Mono -- both from the Braille
// Institute, both SIL Open Font License 1.1, both shipped as variable fonts with a `wght` axis, so
// "two files per family instead of fourteen, and arbitrary weight interpolation for free".
//
// 5.1 is also blunt about the limit: "Atkinson Hyperlegible Next covers 150+ languages -- which
// means Latin, Cyrillic, Greek and their extensions. It does **not** cover CJK, Arabic, Hebrew,
// Devanagari, Thai... Do not ship believing the typeface is universal; a Japanese user would see
// tofu for every glyph."
//
// So the chain matters, and 10.3 specifies it: Atkinson first, then "systemFallbackForScript --
// platform default: Noto CJK, etc." Compose falls through per glyph, so a paragraph mixing English
// and Japanese takes Atkinson for the Latin and the platform's own font for the kana.
//
// Nothing is bundled behind Atkinson, and that is a measured decision rather than an omission.
// Bundling `NotoSans` was tried: it is Latin, Greek and Cyrillic -- the same ground Atkinson
// already covers -- so it added 4.4MB and no scripts. Noto covers Unicode as about 150 separate
// families, and reaching the scripts 5.1 warns about means `NotoSansJP` (9.4MB), `NotoSansArabic`
// and `NotoSansDevanagari` individually, for about 11MB.
//
// Rendered against a document of Japanese, Arabic, Devanagari, Thai and Hebrew, the platform
// fallback shaped all five correctly with none of them bundled. Paying eleven megabytes to replace
// something the platform already does well would be a cost with nothing on the other side of it.
// The trade to watch is determinism: what a reader sees for those scripts is now their platform's
// choice of font, not ours, and it has only been verified on desktop.

/** The weights each variable face is registered at, spanning the scale and 5.5's range. */
private val registeredWeights =
    listOf(
        FontWeight.W300,
        FontWeight.W400,
        FontWeight.W500,
        FontWeight.W600,
        FontWeight.W700,
    )

/**
 * The proportional family: Atkinson Next, then Noto Sans, then whatever the platform has.
 *
 * Registering one variable file at five weights is what lets the `wght` axis be used at all: the
 * entry Compose picks for a given [FontWeight] carries that weight, and a variable font asked for
 * a weight it was registered at renders that instance rather than a synthetic bold.
 */
@Composable
fun proseFontFamily(): FontFamily =
    LocalTypefaces.current?.prose ?: chain(Res.font.atkinson_next, Res.font.atkinson_next_italic)

/** The monospace family, for code blocks and all Fountain content (5.1). */
@Composable
fun monoFontFamily(): FontFamily =
    LocalTypefaces.current?.mono ?: chain(Res.font.atkinson_mono, Res.font.atkinson_mono_italic)

/**
 * Both families, built once for everything inside a [DraftsTheme].
 *
 * Building one is ten composable resource lookups, each with its own remembered state, and every
 * [proseStyleOf] needs a family -- three or four times for each block the editor composes. Built
 * per call, that was a quarter of the cost of composing a row, and paid again when the row was
 * recycled, because each lookup is state to tear down (measured 2026-10-03). Built here, it is paid
 * once per window.
 */
@Immutable
class Typefaces(
    val prose: FontFamily,
    val mono: FontFamily,
)

/**
 * The families [DraftsTheme] built, or null outside one -- where [proseFontFamily] builds its own,
 * as it always did, so a composable tested on its own still gets the right faces.
 */
val LocalTypefaces: ProvidableCompositionLocal<Typefaces?> = staticCompositionLocalOf { null }

/** Both families, remembered: the lookups settle once the font files have loaded, and then hold. */
@Composable
internal fun rememberTypefaces(): Typefaces {
    val prose = chain(Res.font.atkinson_next, Res.font.atkinson_next_italic)
    val mono = chain(Res.font.atkinson_mono, Res.font.atkinson_mono_italic)

    return remember(prose, mono) { Typefaces(prose, mono) }
}

/**
 * A family built from a face and its italic, at every weight the scale asks for.
 *
 * What is *not* here is the point: there is no bundled fallback behind these. A glyph neither file
 * has falls through to the platform, which is what 10.3 asks for and what the scripts test shows
 * working.
 */
@Composable
private fun chain(
    upright: FontResource,
    italic: FontResource,
): FontFamily =
    FontFamily(
        registeredWeights.map { resourceFont(upright, it, FontStyle.Normal) } +
            registeredWeights.map { resourceFont(italic, it, FontStyle.Italic) },
    )
