package com.appthere.drafts.platform.files

import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the desktop keeps app-private data.
 *
 * 8.1 says snapshots go to "app-private storage" and leaves the location to the platform, which on
 * desktop means three different conventions. XDG on Linux, Application Support on macOS, AppData on
 * Windows -- each is where a user's backup tool looks and where an uninstaller cleans up, and a
 * directory invented next to the executable is in neither.
 *
 * `XDG_DATA_HOME` is honoured where it is set, because a reader who has moved their data directory
 * has said where they want this.
 */
fun desktopDataRoot(
    home: String = System.getProperty("user.home").orEmpty(),
    os: String = System.getProperty("os.name").orEmpty(),
    xdg: String? = System.getenv("XDG_DATA_HOME"),
): String {
    val base =
        when {
            os.startsWith("Mac", ignoreCase = true) -> {
                Path.of(home, "Library", "Application Support")
            }

            os.startsWith("Windows", ignoreCase = true) -> {
                System.getenv("APPDATA")?.let(Path::of) ?: Path.of(home, "AppData", "Roaming")
            }

            !xdg.isNullOrBlank() -> {
                Path.of(xdg)
            }

            else -> {
                Path.of(home, ".local", "share")
            }
        }

    return base.resolve(VENDOR).resolve(APPLICATION).toString()
}

/** 7.3 puts each document's snapshot in `sessions/<documentId>/`. */
fun desktopSessionRoot(): String = Path.of(desktopDataRoot(), SESSIONS).toString()

/**
 * A desktop document's session identity.
 *
 * Lives here because working out a display name, a URI and a file kind means handling a path, and
 * `engineering-conventions.md` 4.2 puts every `java.nio.file` import in this module. The Konsist
 * rule caught the first attempt at putting this in the entry point, which is the rule doing its job.
 *
 * The kind is passed in rather than guessed from the extension. 9.1 gives four extensions for
 * Markdown and two for Fountain, and the table that knows them belongs to `:platform-intents`,
 * which is the module about what the operating system hands over. A guess here was wrong for
 * `.spmd` -- a screenplay that opened as prose with every scene heading flattened.
 *
 * `documentId` is the digest of the path rather than 7.3's UUID: a UUID needs an index mapping it
 * back to a file, there is no index yet, and a content-addressed id finds its own snapshot with
 * nothing to consult. Renaming the file outside the application orphans its snapshot -- the cost of
 * not having the index, and what the index will fix.
 */
fun desktopIdentity(
    path: String,
    kind: String,
): SessionIdentity {
    val file = Path.of(path).toAbsolutePath().normalize()

    return SessionIdentity(
        documentId = sha256(file.toString().encodeToByteArray()).hex,
        uri = file.toUri().toString(),
        displayName = file.fileName.toString(),
        kind = kind,
        // 7.3: "**Desktop:** absolute path, with existence re-checked on restore." Absolute and
        // normalised, so a session recorded from a relative path still resolves from a different
        // working directory on the next launch.
        accessToken = file.toString(),
    )
}

/**
 * Resolves a desktop access token back to a document, or null if it no longer leads anywhere.
 *
 * The re-check 7.3 asks for. A path is not a permission -- it is a guess that survived a restart --
 * and files get moved, renamed and deleted between sessions. 7.3 says what to do when this returns
 * null: "A document whose file has vanished opens read-only from its snapshot with a clear banner
 * offering *Save As*."
 */
fun resolveDesktopToken(token: String): DocumentRef? =
    Path.of(token).takeIf { Files.isReadable(it) }?.let { DocumentRef(it.toString()) }

private const val VENDOR = "AppThere"
private const val APPLICATION = "Drafts"
private const val SESSIONS = "sessions"
