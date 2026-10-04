package com.appthere.drafts.platform.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/**
 * Where Android keeps app-private data.
 *
 * `filesDir` and not external storage: 8.1 says snapshots go to "app-private storage", and on
 * Android that phrase has a precise meaning -- a directory no other application can read, that
 * needs no permission, and that the system removes when the application is uninstalled. External
 * storage is none of those things.
 */
fun androidDataRoot(context: Context): String = File(context.filesDir, DATA).path

/** 7.3 puts each document's snapshot in `sessions/<documentId>/`. */
fun androidSessionRoot(context: Context): String = File(androidDataRoot(context), SESSIONS).path

/** 5.5's settings, one file per document type, beside the sessions rather than inside them. */
fun androidSettingsRoot(context: Context): String = File(androidDataRoot(context), SETTINGS).path

/**
 * A session identity for a document the reader picked through the Storage Access Framework.
 *
 * 7.3: "**Android:** `takePersistableUriPermission` on the content URI at open time. Without this,
 * the URI is dead on next launch and restoration silently fails." Taking the permission *is* the
 * access token -- the URI string is only how it is named afterwards -- so it happens here, at open,
 * and not at restore when the grant would already be gone.
 *
 * The kind is passed in rather than guessed. 9.1 gives Markdown four extensions and Fountain two,
 * and 9.2 warns that the MIME type may be `application/octet-stream` whatever the file is -- so
 * deciding needs the name, the type and sometimes the contents, which is the caller's job.
 *
 * Returns null when the grant cannot be taken. That is not an error worth throwing: it means this
 * document cannot be restored next launch, and the caller may still want to open it now.
 */
fun androidIdentity(
    context: Context,
    uri: Uri,
    kind: String,
): SessionIdentity? {
    val granted =
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess

    val name = displayNameOf(context.contentResolver, uri) ?: uri.lastPathSegment ?: return null

    return SessionIdentity(
        documentId = androidDocumentId(uri),
        uri = uri.toString(),
        displayName = name,
        kind = kind,
        accessToken = if (granted) uri.toString() else null,
    )
}

/**
 * The 7.3 document id of a file reached through [uri]: the same URI is always the same document.
 *
 * Its own function because two places need it to agree -- the identity recorded when a document is
 * opened, and the launcher asking which documents still have a task (`forgetClosedWindows`).
 */
fun androidDocumentId(uri: Uri): String = sha256(uri.toString().encodeToByteArray()).hex

/**
 * Resolves an Android access token back to a document, or null if the grant has lapsed.
 *
 * 7.3's re-check, in Android's terms. A persisted permission is not permanent: the reader can
 * revoke it, the provider can be uninstalled, and the system drops grants for documents that no
 * longer exist. Checking the held grants is cheaper and more honest than opening the file to find
 * out.
 */
fun resolveAndroidToken(
    context: Context,
    token: String,
): DocumentRef? {
    val uri = runCatching { Uri.parse(token) }.getOrNull() ?: return null
    val held = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    return if (held) DocumentRef(token) else null
}

fun displayNameOf(
    resolver: ContentResolver,
    uri: Uri,
): String? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

/** Whether [uri] is a Storage Access Framework document rather than a path in disguise. */
internal fun isDocumentUri(uri: Uri): Boolean =
    uri.scheme == ContentResolver.SCHEME_CONTENT || DocumentsContract.isDocumentUri(null, uri)

private const val DATA = "drafts"
private const val SESSIONS = "sessions"
private const val SETTINGS = "settings"
