package com.appthere.drafts.app

import androidx.compose.ui.unit.sp
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.design.FocusMode
import com.appthere.drafts.design.MotionPreference
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.Theme
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.sha256
import com.appthere.drafts.platform.intents.DocumentKind
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
    /**
     * Null in files written before motion became a three-way choice. [toSettings] reads the old
     * boolean when it is, so a reader who had asked for reduced motion keeps it.
     */
    val motion: String? = null,
    /** Only ever read, never written. See [motionPreference]. */
    val reducedMotion: Boolean = false,
    val typewriterScrolling: Boolean,
    val focusMode: String,
    val autoHideChrome: Boolean,
    /** Defaulted: it arrived after files were already being written. */
    val collapseNotes: Boolean = false,
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

    /**
     * 7.4: "An untitled document starts as the kind the reader last created -- Markdown on first
     * launch."
     *
     * Beside the per-kind settings, because it is the same sort of thing: a choice the reader made,
     * kept so they do not have to make it again. Anything unreadable is the first launch.
     */
    suspend fun kindForNew(): DocumentKind =
        runCatching { store.read(newKindRef).text.trim() }
            .getOrNull()
            ?.let { id -> DocumentKind.entries.firstOrNull { it.id == id } }
            ?: DocumentKind.Markdown

    /** Remembers [kind] as the one the reader last created, for the next new document. */
    suspend fun rememberKindForNew(kind: DocumentKind): Boolean =
        store.writeAtomically(newKindRef, kind.id) is WriteOutcome.Written

    /**
     * 11.3's words for one screenplay -- "a per-document configurable prefix list and transition
     * suffix" -- or Fountain 1.1's English when none were chosen for it.
     *
     * Kept here rather than in the file. The file stays exactly the reader's, and stays valid
     * Fountain to every other application: its headings are readable there once they are forced
     * with `.`, which is 11.3's escape hatch. The cost is that the choice stays on this machine,
     * and a file moved outside the application leaves it behind.
     */
    suspend fun keywordsFor(document: SessionIdentity): FountainKeywords =
        runCatching {
            format.decodeFromString(KeywordsRecord.serializer(), store.read(keywordsRef(document)).text).toKeywords()
        }.getOrNull() ?: FountainKeywords.ENGLISH

    /** Remembers [keywords] as the words [document] is read with. */
    suspend fun rememberKeywords(
        document: SessionIdentity,
        keywords: FountainKeywords,
    ): Boolean {
        val encoded =
            format.encodeToString(
                KeywordsRecord.serializer(),
                KeywordsRecord(keywords.sceneHeadingPrefixes, keywords.transitionSuffix),
            )
        return store.writeAtomically(keywordsRef(document), encoded) is WriteOutcome.Written
    }

    /**
     * Carries [from]'s words to [to], where *Save As* has just put the document. True if there were
     * none to carry.
     *
     * The words are kept under the file, so they have to follow it to its new one: an untitled
     * screenplay, saved, would otherwise reopen next time in English.
     */
    suspend fun keywordsMoved(
        from: SessionIdentity,
        to: SessionIdentity,
    ): Boolean {
        val chosen =
            runCatching { store.read(keywordsRef(from)).text }.getOrNull() ?: return true
        return store.writeAtomically(keywordsRef(to), chosen) is WriteOutcome.Written
    }

    private fun refFor(kind: String) = DocumentRef("$root/$kind.json")

    /**
     * Under the file, for a document that has one, and under the document's own id for one that
     * does not yet. The file and not the id because a document saved from untitled keeps its
     * untitled id while it is open but is known by its file when next opened (`divergences.md`,
     * 7.3) -- and the file's URI is the one thing both sessions agree on.
     */
    private fun keywordsRef(document: SessionIdentity): DocumentRef {
        val key = document.uri?.let { sha256(it.encodeToByteArray()).hex } ?: document.documentId
        return DocumentRef("$root/$KEYWORDS/$key.json")
    }

    private val newKindRef get() = DocumentRef("$root/$NEW_KIND")

    private companion object {
        /** Not `.json`: it holds one word, and a per-kind settings file is never named for it. */
        const val NEW_KIND = "new-document-kind"

        /** A directory, one file to a screenplay that has words of its own. */
        const val KEYWORDS = "screenplay-words"

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

/** 11.3's words as they go to disk. */
@Serializable
internal data class KeywordsRecord(
    val sceneHeadingPrefixes: List<String>,
    val transitionSuffix: String,
)

/**
 * Back to keywords, through the same check a reader's typing goes through: a file can be edited by
 * hand, and a blank suffix in one would make every uppercase line a transition. Null if it fails.
 */
private fun KeywordsRecord.toKeywords(): FountainKeywords? = FountainKeywords.of(sceneHeadingPrefixes, transitionSuffix)

private fun ReaderSettings.toRecord() =
    ReaderSettingsRecord(
        palette = theme.id,
        baseSp = base.value,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphSpacing = paragraphSpacing,
        characters = characters,
        bodyWeight = bodyWeight,
        motion = motion.name,
        typewriterScrolling = typewriterScrolling,
        focusMode = focusMode.name,
        autoHideChrome = autoHideChrome,
        collapseNotes = collapseNotes,
    )

/**
 * The motion preference a file expresses, whichever way it expresses it.
 *
 * Motion used to be a boolean, so files exist that say `reducedMotion` and nothing else. Reading
 * those as the new default would silently give animation back to a reader who had turned it off,
 * which is the one group of readers for whom that is not a small matter.
 *
 * A name that is not a preference falls back to the default rather than failing the load.
 */
private fun ReaderSettingsRecord.motionPreference(): MotionPreference =
    motion?.let(MotionPreference::named)
        ?: if (reducedMotion) MotionPreference.Reduced else ReaderSettings().motion

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
        theme = Theme.withId(palette) ?: ReaderSettings().theme,
        base = baseSp.sp,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphSpacing = paragraphSpacing,
        characters = characters,
        bodyWeight = bodyWeight,
        motion = motionPreference(),
        typewriterScrolling = typewriterScrolling,
        focusMode = FocusMode.entries.firstOrNull { it.name == focusMode } ?: FocusMode.Off,
        autoHideChrome = autoHideChrome,
        collapseNotes = collapseNotes,
    ).clamped()
