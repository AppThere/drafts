package com.appthere.drafts.a11y

import androidx.compose.runtime.Composable
import java.util.concurrent.TimeUnit

/**
 * The desktop's answer, asked of whichever desktop this is.
 *
 * There is no portable way to ask. The JDK exposes nothing, and each desktop keeps the preference
 * somewhere different, so this asks each one the way that desktop answers -- through the tool it
 * ships with, because the alternative is a native binding and a dependency for a single boolean.
 *
 * Asked once per process and remembered. It is a subprocess, which is not something to run on a
 * frame; and a reader who changes the setting mid-session sees it at the next launch, which is a
 * cost worth the one that is avoided.
 *
 * Anything that fails, times out, or is not understood reads as "no preference expressed". A
 * desktop that cannot be asked is not a desktop that asked for less motion, and guessing otherwise
 * would take animation away from readers who never requested it.
 */
@Composable
actual fun systemPrefersReducedMotion(): Boolean = desktopPreference

/** Computed on first read, then kept. See the note above about subprocesses. */
private val desktopPreference: Boolean by lazy { askTheDesktop() }

private fun askTheDesktop(): Boolean {
    val os = System.getProperty("os.name").orEmpty().lowercase()

    return when {
        os.startsWith("mac") -> reduceMotionOnMacOs()
        os.startsWith("windows") -> false
        else -> animationsDisabledOnLinux()
    }
}

/**
 * macOS keeps it in the universal access preferences, where the Accessibility pane writes it.
 *
 * `defaults` exits non-zero when the key has never been set, which is the common case and means
 * the default: motion is fine.
 */
private fun reduceMotionOnMacOs(): Boolean = ask("defaults", "read", "com.apple.universalaccess", "reduceMotion") == "1"

/**
 * GNOME, and the desktops that follow its schema, keep it as `enable-animations`.
 *
 * Phrased the other way round from everyone else -- false means reduce -- which is why this is not
 * simply a string comparison shared with the branch above.
 *
 * KDE keeps the same idea in `kdeglobals` and others keep it nowhere at all; those read as no
 * preference, which is the honest answer rather than a guess.
 */
private fun animationsDisabledOnLinux(): Boolean =
    ask("gsettings", "get", "org.gnome.desktop.interface", "enable-animations") == "false"

/** Runs a short query and returns its first line, or null if it cannot be run. */
private fun ask(vararg command: String): String? =
    runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val answer =
            process.inputStream
                .bufferedReader()
                .use { it.readLine() }
                ?.trim()

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() == 0) answer else null
    }.getOrNull()

/** Long enough for a settings query, short enough that a hung one does not hold up a launch. */
private const val TIMEOUT_SECONDS = 2L
