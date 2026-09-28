package com.appthere.drafts.app.desktop

import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList

/**
 * What a launch opens, per `appthere-drafts.md` 7.3 and 7.4.
 *
 * Every session still open from last time (7.3: "On launch, restore every session"), and the
 * document the launch was asked to open, if any. With neither, one untitled document: "Launching
 * with nothing to restore opens one untitled document, ready to type into."
 *
 * Only then. Sessions to restore mean the reader is picking up where they left off, and 7.4 is
 * explicit that "a launch that always added a blank window would leave one to close every time".
 *
 * Markdown, for now. 7.4 says "the kind the reader last created", and the thing that makes a
 * reader's choice of kind -- the switch in the chrome -- is what will remember it.
 */
internal suspend fun sessionsAtLaunch(
    sessions: SessionList,
    path: String?,
    untitledName: String,
): List<SessionRecord> {
    val open = sessions.restorable().toMutableList()
    path?.let { open.show(it, sessions) }

    if (open.isEmpty()) {
        open += sessions.opened(SessionIdentity.untitled(kind = DocumentKind.Markdown.id, displayName = untitledName))
    }
    return open
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
internal suspend fun MutableList<SessionRecord>.show(
    path: String,
    sessions: SessionList,
) {
    val kind = DocumentKind.of(path) ?: DocumentKind.Markdown
    val identity = desktopIdentity(path, kind.id)
    if (any { it.uri == identity.uri }) return

    val record = sessions.opened(identity)
    if (none { it.documentId == record.documentId }) this += record
}
