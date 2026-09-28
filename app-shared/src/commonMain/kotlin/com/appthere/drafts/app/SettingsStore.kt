package com.appthere.drafts.app

import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.FocusMode
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.Theme
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.WriteOutcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The 5.5 controls as they go to disk.
 *
 * A record of its own rather than serialising [ReaderSettings] directly. That type holds a
 * `Palette` -- four colours and a contrast floor -- and a `TextUnit`, and writing either of those
 * into a file would pin a palette's exact colours into a settings file that outlives the palette.
 * A reader who set "sepia" wants sepia as it is now, not as it was the day they chose it.
 */
@Serializable
internal data class ReaderSettingsRecord(
    /** The theme's name. Still called `palette` because files written before "system" existed say so. */
    val palette: String,
    val baseSp: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    /**
     * Defaulted, unlike its neighbours, because it arrived after files were already being written.
     * A field those files lack must not make them unreadable, or adding a setting would reset every
     * other one a reader had chosen.
     */
    val paragraphSpacing: Float = Prose.Body.spaceAfter,
    val characters: Float,
    val bodyWeight: Int,
    val reducedMotion: Boolean,
    val typewriterScrolling: Boolean,
    val focusMode: String,
    val autoHideChrome: Boolean,
)

/**
 * 5.5: "Exposed in settings, **persisted per document type**."
 *
 * Per type and not per document, which is the spec's own choice and a good one: a screenplay and a
 * novel want different measures and different weights, and nobody wants to set that up again for
 * every chapter file.
 *
 * Settings live beside the sessions in app-private storage, so they go through the same store as
 * everything else and inherit 8.1's atomic write -- a settings file half-written by a crash would
 * come back as no settings at all, and the reader would find their typography reset.
 *
 * A file that cannot be read is treated as absent. Settings are a convenience; losing them costs a
 * reader some fiddling, and refusing to open their document over it would cost a great deal more.
 */
class SettingsStore(
    private val store: DocumentStore,
    private val root: String,
) {
    /** What was saved for [kind], or null if nothing was. */
    suspend fun settingsFor(kind: String): ReaderSettings? =
        runCatching {
            format.decodeFromString(ReaderSettingsRecord.serializer(), store.read(refFor(kind)).text).toSettings()
        }.getOrNull()

    /** Remembers [settings] as the ones for [kind]. */
    suspend fun remember(
        kind: String,
        settings: ReaderSettings,
    ): Boolean {
        val encoded = format.encodeToString(ReaderSettingsRecord.serializer(), settings.toRecord())

        return store.writeAtomically(refFor(kind), encoded) is WriteOutcome.Written
    }

    private fun refFor(kind: String) = DocumentRef("$root/$kind.json")

    private companion object {
        /**
         * Lenient on read, so a settings file written by a later version opens in an earlier one
         * with the fields it understands rather than being discarded whole.
         */
        val format =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }
    }
}

private fun ReaderSettings.toRecord() =
    ReaderSettingsRecord(
        palette = theme.name,
        baseSp = base.value,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphSpacing = paragraphSpacing,
        characters = characters,
        bodyWeight = bodyWeight,
        reducedMotion = reducedMotion,
        typewriterScrolling = typewriterScrolling,
        focusMode = focusMode.name,
        autoHideChrome = autoHideChrome,
    )

/**
 * Back to settings, clamped.
 *
 * [ReaderSettings.clamped] is applied on the way in because a settings file is a file: it can be
 * edited by hand, written by a version with different limits, or corrupted into something that
 * parses. 5.5 gives every control a range, and the ranges are what keep a document legible.
 *
 * An unknown theme name falls back to the default rather than failing the load. Palettes come
 * and go; the rest of somebody's typography should not go with one.
 */
private fun ReaderSettingsRecord.toSettings(): ReaderSettings =
    ReaderSettings(
        theme = Theme.named(palette) ?: ReaderSettings().theme,
        base = baseSp.sp,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphSpacing = paragraphSpacing,
        characters = characters,
        bodyWeight = bodyWeight,
        reducedMotion = reducedMotion,
        typewriterScrolling = typewriterScrolling,
        focusMode = FocusMode.entries.firstOrNull { it.name == focusMode } ?: FocusMode.Off,
        autoHideChrome = autoHideChrome,
    ).clamped()
