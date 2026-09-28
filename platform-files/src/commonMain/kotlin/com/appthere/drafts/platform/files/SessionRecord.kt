package com.appthere.drafts.platform.files

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What a session needs to describe itself, beyond what the document already knows.
 *
 * For a file-backed document, [documentId] is derived from its location rather than being a fresh
 * UUID -- a divergence from 7.3's `"documentId": "uuid"`: a content-addressed id finds its own
 * snapshot with nothing to consult, at the cost that renaming the file outside the application
 * orphans its snapshot. An untitled document (7.4) has no location, so it gets a real UUID from
 * [untitled], and [uri] is null until its first save.
 */
data class SessionIdentity(
    val documentId: String,
    val uri: String?,
    val displayName: String,
    val kind: String,
    val accessToken: String? = null,
) {
    companion object {
        /** A new untitled document of [kind], under a UUID nothing else has. */
        @OptIn(ExperimentalUuidApi::class)
        fun untitled(
            kind: String,
            displayName: String,
        ): SessionIdentity =
            SessionIdentity(documentId = Uuid.random().toString(), uri = null, displayName = displayName, kind = kind)
    }
}

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
 * [closedAt] separates "open" from "has a snapshot". 7.3 says "On launch, restore every session",
 * and a session file outlives the document being closed -- 8.3 keeps the snapshot for thirty days
 * after a save. Restoring every file on disk would reopen documents the reader shut months ago, so
 * a record is a *restorable* session only while this is null.
 *
 * [window] is null until the document has a window with a size, which on desktop is immediately
 * and on a phone is never.
 *
 * [uri] and [baseDigest] are null for an untitled document (7.4): it has no file, so nothing to
 * point at and nothing to compare against. Both were always present in files written before
 * untitled documents existed, so those still read.
 */
@Serializable
data class SessionRecord(
    val documentId: String,
    val uri: String?,
    val displayName: String,
    val kind: String,
    val caret: CaretRecord,
    @SerialName("scrollOffset") val scrollOffset: Int,
    @SerialName("baseDigest") val baseDigest: String?,
    @SerialName("snapshotPath") val snapshotPath: String,
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("savedAt") val savedAt: Long? = null,
    @SerialName("closedAt") val closedAt: Long? = null,
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
