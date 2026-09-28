package com.appthere.drafts.platform.files

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithSecurityScope
import platform.Foundation.NSUserDomainMask

/**
 * Where an Apple platform keeps app-private data.
 *
 * The Documents directory inside the application's own container. On iOS that container is private
 * to the application and removed with it, which is what 8.1 means by "app-private storage". The
 * same call answers on macOS, where the container is the sandbox's.
 */
@OptIn(ExperimentalForeignApi::class)
fun appleDataRoot(): String {
    val directories = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)

    return (directories.firstOrNull() as? String ?: TEMPORARY_FALLBACK) + "/" + DATA
}

/** 7.3 puts each document's snapshot in `sessions/<documentId>/`. */
fun appleSessionRoot(): String = appleDataRoot() + "/" + SESSIONS

/**
 * Resolves an iOS security-scoped bookmark back to a document, or null if it no longer leads
 * anywhere.
 *
 * 7.3: "**iOS:** security-scoped bookmark data, resolved with
 * `startAccessingSecurityScopedResource`." A bookmark survives the file being moved or renamed,
 * which a path does not -- that is the whole reason iOS uses one -- but it does not survive the
 * file being deleted, or the reader revoking access, which is why resolving it can fail.
 *
 * The caller must call `startAccessingSecurityScopedResource` before reading and stop afterwards.
 * That pairing belongs to whoever holds the document open, not here: this resolves the token and
 * says whether it still points at something.
 *
 * Compiled but never run. Nothing in this project has executed on an Apple target; `check` only
 * compiles them.
 */
@OptIn(ExperimentalForeignApi::class)
fun resolveAppleBookmark(bookmark: NSData): DocumentRef? =
    memScoped {
        val stale = alloc<BooleanVar>()
        val url =
            NSURL.URLByResolvingBookmarkData(
                bookmark,
                NSURLBookmarkResolutionWithSecurityScope,
                null,
                stale.ptr,
                null,
            )

        // A stale bookmark still resolves -- the file moved and the bookmark followed it, which is
        // the reason iOS uses bookmarks rather than paths. It should be rewritten by the caller,
        // which is a job for whoever owns the session record.
        url?.path?.let { DocumentRef(it) }
    }

private const val DATA = "drafts"
private const val SESSIONS = "sessions"

/** Only reachable if the system reports no Documents directory at all, which it always has. */
private const val TEMPORARY_FALLBACK = "/tmp"
