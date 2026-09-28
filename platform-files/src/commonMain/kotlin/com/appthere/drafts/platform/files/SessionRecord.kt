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
 * [accessToken] and [window] are nullable because nothing produces them yet. 7.3 makes the token
 * the crux -- a persisted URI permission on Android, a security-scoped bookmark on iOS -- and each
 * of those is platform work that has not been done. A record written now is honest about not having
 * them rather than carrying a placeholder that later code would trust.
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
