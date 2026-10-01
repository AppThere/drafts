package com.appthere.drafts.app.desktop

import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.intents.DocumentKind

/**
 * Where a desktop *Save As* is going, in the terms the session list keeps.
 *
 * The one part of 7.4's first save that is the desktop's own: a path from a save dialog, made
 * absolute and given a display name by `desktopIdentity`. `SaveAs` in `:app-shared` does the rest,
 * identically to Android, whose picker returns a `content://` URI instead.
 *
 * The extension the reader chose decides the kind (9.1); a name with none keeps the kind the
 * document already had, because a reader who typed `draft` meant to keep writing what they were
 * writing.
 */
internal fun destinationOf(
    path: String,
    record: SessionRecord,
): SessionIdentity = desktopIdentity(path, DocumentKind.of(path)?.id ?: record.kind)
