package com.appthere.drafts.app.desktop

import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.intents.LaunchRequest
import com.appthere.drafts.platform.windows.SessionList

/**
 * What a launch opens, per `appthere-drafts.md` 7.3 and 7.4.
 *
 * Every session still open from last time (7.3: "On launch, restore every session"), and whatever
 * the launch asked for -- a file, or a new document from the launcher's own entry points. With
 * nothing restored and nothing asked for, one untitled document: "Launching with nothing to
 * restore opens one untitled document, ready to type into."
 *
 * Only then. Sessions to restore mean the reader is picking up where they left off, and 7.4 is
 * explicit that "a launch that always added a blank window would leave one to close every time".
 *
 * A new document is [newKind] unless the request named one: "the kind the reader last created --
 * Markdown on first launch".
 */
internal suspend fun sessionsAtLaunch(
    sessions: SessionList,
    request: LaunchRequest?,
    untitledName: String,
    newKind: DocumentKind = DocumentKind.Markdown,
): List<SessionRecord> {
    val open = sessions.restorable().toMutableList()
    request?.let { open.serve(it, sessions, untitledName, newKind) }

    if (open.isEmpty()) open.serve(LaunchRequest.New(), sessions, untitledName, newKind)
    return open
}

/**
 * Answers one [request], at launch or later: from a second launch handed over to this one, or from
 * the macOS Dock. A file opens in its own window unless it already has one (9.4); a new document is
 * always a new window, because a reader who asks for a new document has asked for one (7.4).
 */
internal suspend fun MutableList<SessionRecord>.serve(
    request: LaunchRequest,
    sessions: SessionList,
    untitledName: String,
    newKind: DocumentKind,
) {
    when (request) {
        is LaunchRequest.Open -> {
            show(request.path, sessions)
        }

        is LaunchRequest.New -> {
            val kind = request.kind ?: newKind
            this += sessions.opened(SessionIdentity.untitled(kind = kind.id, displayName = untitledName))
        }
    }
}

/**
 * Adds a document to the session list and to the windows on screen, unless it is already there.
 *
 * Already-open is the ordinary case when a reader double-clicks a file they have open: 9.4 asks
 * for a new window per document, not per double-click.
 *
 * "Already there" is by file, not by id. A document saved from untitled (7.4) keeps the UUID it was
 * given, so its id is not the one this path would produce -- and matching on ids would open the
 * file a second time, in a second window, as a second session.
 */
private suspend fun MutableList<SessionRecord>.show(
    path: String,
    sessions: SessionList,
) {
    val kind = DocumentKind.of(path) ?: DocumentKind.Markdown
    val identity = desktopIdentity(path, kind.id)
    if (any { it.uri == identity.uri }) return

    val record = sessions.opened(identity)
    if (none { it.documentId == record.documentId }) this += record
}
