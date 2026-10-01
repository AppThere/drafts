package com.appthere.drafts.app.android

import android.content.Intent
import android.net.Uri
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList

/**
 * What a launch opens, per `appthere-drafts.md` 7.3 and 7.4.
 *
 * Three sentences, in order:
 *
 * - "Launching with sessions to restore restores them (7.3) and opens nothing else -- a launch that
 *   always added a blank window would leave one to close every time."
 * - "Launching with nothing to restore opens one untitled document, ready to type into."
 * - "Launching while the application is already running opens a new untitled document in a new
 *   window: the reader asked for the application again, and it is already showing everything else
 *   they had open."
 *
 * [requested] is the kind a launcher shortcut asked for -- 7.4's "New Markdown document" and "New
 * Fountain screenplay" -- and null for a plain tap on the icon. A shortcut always opens its
 * document, on a cold launch alongside whatever was restored: the reader asked for a new one and
 * also has their work from last time.
 *
 * [showing] is whether this application already has documents on screen. On Android that is a
 * question about tasks rather than windows, which is why it is asked outside and answered here.
 *
 * Separate from the Activity because it is the part worth testing, and an Activity is the part that
 * is not. The desktop's equivalent is `Launch.kt` in `:app-desktop`, which has the same three rules
 * and two more besides -- it can be asked to open a file by path, which on Android arrives as an
 * intent to `DocumentActivity` and never comes through here.
 */
internal suspend fun documentsAtLaunch(
    sessions: SessionList,
    requested: DocumentKind?,
    showing: Boolean,
    untitledName: String,
    lastKind: DocumentKind,
): List<SessionRecord> {
    // Already showing means everything restorable is already on screen; restoring it again would
    // only shuffle Recents.
    val open = if (showing) mutableListOf() else sessions.restorable().toMutableList()

    // 7.4's "the kind the reader last created -- Markdown on first launch", unless a shortcut said.
    val kind = requested ?: lastKind.takeIf { showing || open.isEmpty() }
    kind?.let { open += sessions.opened(SessionIdentity.untitled(kind = it.id, displayName = untitledName)) }

    return open
}

/**
 * How a document this application already knows about is named to [DocumentActivity].
 *
 * A session rather than a file: an untitled document (7.4) has no file, and the ones that do are
 * reached through their `content://` URI anyway. The id goes in the intent's **data** rather than
 * an extra because `documentLaunchMode="intoExisting"` keys a task on the data -- so one id is one
 * task, which is what gives every open document its own entry in Recents (7.2).
 *
 * The scheme is this application's own and is never declared in an intent filter. These intents are
 * explicit; nothing outside can send one.
 */
internal fun sessionUri(documentId: String): Uri = Uri.parse("$SESSION_SCHEME://document/$documentId")

/** The session id [sessionUri] encoded, or null if this intent is about something else. */
internal fun sessionIdOf(intent: Intent): String? = intent.data?.takeIf { it.scheme == SESSION_SCHEME }?.lastPathSegment

private const val SESSION_SCHEME = "drafts-session"
