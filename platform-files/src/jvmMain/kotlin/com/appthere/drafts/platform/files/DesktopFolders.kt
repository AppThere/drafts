package com.appthere.drafts.platform.files

import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The few questions the desktop's project windows ask about paths, kept here with every other path
 * on the desktop (engineering conventions 4.2): a folder's name, whether it is still there, and
 * where a file sits within one.
 */
fun nameOfPath(path: String): String = Paths.get(path).fileName?.toString() ?: path

/** Whether [path] is a folder that is there now: a recent project that can still be opened. */
fun isFolder(path: String): Boolean = Files.isDirectory(Paths.get(path))

/**
 * The folder [file] is in, relative to [folder] -- "Screenplay", or "" for the top -- or null if
 * [file] is not inside [folder] at all.
 */
fun folderWithin(
    file: String,
    folder: String,
): String? {
    val inside = Paths.get(file).toAbsolutePath().normalize()
    val top = Paths.get(folder).toAbsolutePath().normalize()
    return if (inside.startsWith(top) && inside != top) top.relativize(inside.parent).toString() else null
}

/**
 * [path] handed to the system's default application (`projects.md` 13.3: "open in the system's
 * default application"). False where the desktop offers no such thing, which a headless or bare
 * Linux session may not.
 */
fun openWithSystem(path: String): Boolean =
    runCatching {
        val supported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
        if (supported) Desktop.getDesktop().open(File(path))
        supported
    }.getOrDefault(false)
