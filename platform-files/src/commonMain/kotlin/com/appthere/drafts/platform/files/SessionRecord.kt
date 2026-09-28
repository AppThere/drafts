package com.appthere.drafts.platform.files

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a session needs to describe itself, beyond what the document already knows.
 *
 * [documentId] is derived from the document's location rather than being a fresh UUID, which is a
 * divergence from 7.3's `"documentId": "uuid"` and a deliberate one: a UUID needs an index
 * somewhere mapping it back to a file, and there is no index yet. A content-addressed id finds its
 * own snapshot with nothing to consult. The cost is that renaming a file outside the application
 * orphans its snapshot; 7.3's session list, when it exists, is what fixes that.
 */
data class SessionIdentity(
    val documentId: String,
    val uri: String,
    val displayName: String,
    val kind: String,
    val accessToken: String? = null,
)

/** Where the caret was, in the terms 7.3 stores it. */
@Serializable
data class CaretRecord(
    val blockIndex: Int,
    val offset: Int,
)

/** Window geometry, for the platforms that have windows. */
@Serializable
data class WindowRecord(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val placement: String,
)

/**
 * The per-document session file of `appthere-drafts.md` 7.3, written beside the snapshot.
 *
 * 8.1: "Snapshot directory also holds `meta.json` (7.3) so caret and scroll survive with the text."
 * Without it a restored snapshot would open at the top of the document with the caret nowhere,
 * which for a long manuscript is most of the value of restoring it at all.
 *
 * [accessToken] is 7.3's crux and differs per platform: on desktop it is the absolute path, with
 * existence re-checked on restore. The Android persisted URI permission and the iOS security-scoped
 * bookmark arrive with those platforms' stores, so the field stays nullable rather than carrying a
 * placeholder that later code would trust.
 *
 * [savedAt] is what 8.3's retention is measured from -- "Retain snapshots for 30 days after a
 * successful save, then prune". Null means no save has happened since this snapshot was written,
 * and a snapshot that was never superseded by a save is never pruned. That is the whole of "Never
 * auto-discard a snapshot": the clock only ever starts once the work is safely in the file.
 *
 * [window] is nullable because window geometry is Phase 5's, along with restoring windows at all.
 */
@Serializable
data class SessionRecord(
    val documentId: String,
    val uri: String,
    val displayName: String,
    val kind: String,
    val caret: CaretRecord,
    @SerialName("scrollOffset") val scrollOffset: Int,
    @SerialName("baseDigest") val baseDigest: String,
    @SerialName("snapshotPath") val snapshotPath: String,
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("savedAt") val savedAt: Long? = null,
    val window: WindowRecord? = null,
) {
    companion object {
        /**
         * Lenient on read, tidy on write.
         *
         * `ignoreUnknownKeys` is what lets a session file written by a later version open in an
         * earlier one instead of throwing: 7.3 will gain fields, and the file holds the location of
         * somebody's unsaved work, so refusing to parse it is the worst available outcome.
         * `encodeDefaults` keeps the nulls visible in the file, because a reader looking at it to
         * find out why a session did not restore should see that the token was absent.
         */
        val format: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = true
            }
    }
}
