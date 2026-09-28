package com.appthere.drafts.platform.files

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * 7.4's *Save As* on the desktop: "through the platform's own picker ... a save dialog on desktop".
 *
 * Here rather than in `:platform-intents` because a save picker is document access: it decides
 * where a reader's words are written, and on Android the picker's answer *is* the permission to
 * write there (7.3's `takePersistableUriPermission`). It also keeps every path on the desktop in
 * this module, as 4.2 of the conventions asks.
 *
 * `java.awt.FileDialog` rather than Swing's `JFileChooser`, because it is the platform's own:
 * the native panel on macOS and Windows and GTK's on Linux. That is the dialog a reader already
 * knows, with their sidebar and recent places, and it is the one that asks before replacing a file
 * that is already there -- which is what lets the save that follows skip 8.2's digest check.
 *
 * Modal, and blocks until the reader answers, so it is called from the UI thread with [parent] as
 * its owner. [near] is a file whose folder the dialog should open in, usually the document's own;
 * without one it opens in the reader's Documents folder where there is one. Returns the chosen path,
 * or null if the reader cancelled.
 */
fun chooseSaveLocation(
    parent: Frame?,
    title: String,
    suggestedName: String,
    near: String? = null,
): String? {
    val dialog =
        FileDialog(parent, title, FileDialog.SAVE).apply {
            file = suggestedName
            directory = near?.let { File(it).parent } ?: defaultFolder()
        }
    dialog.isVisible = true

    val name = dialog.file ?: return null
    return File(dialog.directory, name).path
}

/** Documents, where the reader keeps their writing, or their home folder if there is no such thing. */
private fun defaultFolder(): String {
    val home = File(System.getProperty("user.home"))
    val documents = File(home, "Documents")
    return if (documents.isDirectory) documents.path else home.path
}
