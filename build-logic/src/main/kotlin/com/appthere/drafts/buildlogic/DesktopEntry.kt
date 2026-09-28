package com.appthere.drafts.buildlogic

import java.io.File
import java.nio.file.Files

/**
 * Corrects the `.desktop` entry inside a `.deb` that jpackage has already built.
 *
 * `appthere-drafts.md` 9.4 asks for "a `.desktop` file with `MimeType=text/markdown;` ... `%f` in
 * `Exec` delivers the path." jpackage's entry has no `%f`, so a double-clicked document starts the
 * application without being told which one; and it writes `MimeType` once per extension, so the
 * line repeats itself and names only the types given file associations.
 *
 * Done to the finished package because there is no earlier point to do it. jpackage would take a
 * replacement template from `--resource-dir`, but the Compose plugin passes its own directory after
 * any arguments a build adds, and clears that directory as the task starts.
 *
 * Every change is checked for: an entry without the expected line fails the build rather than
 * shipping a package that quietly still cannot open a file.
 */
object DesktopEntry {
    /**
     * One of the entry's actions: a `[Desktop Action]` group, which launchers show in the
     * application's context menu. 7.4's *New Markdown document* and *New Fountain screenplay* are
     * these on Linux. [arguments] follow the application's own command.
     */
    data class Action(
        val id: String,
        val name: String,
        val arguments: String,
    )

    /**
     * Rewrites the entry inside [deb] in place, declaring [mimeTypes], taking a path, and offering
     * [actions].
     */
    fun fixDeb(
        deb: File,
        mimeTypes: List<String>,
        actions: List<Action> = emptyList(),
    ) {
        val work = Files.createTempDirectory("drafts-deb").toFile()
        try {
            run("dpkg-deb", "--raw-extract", deb.path, work.path)

            val entries = work.walk().filter { it.isFile && it.name.endsWith(".desktop") }.toList()
            val entry = entries.singleOrNull() ?: error("Expected one .desktop file in $deb, found ${entries.size}")
            entry.writeText(fixed(entry.readText(), mimeTypes, actions))

            // Root-owned, as a package's files must be; `fakeroot` because this build is not root.
            run("fakeroot", "dpkg-deb", "--build", "--root-owner-group", work.path, deb.path)
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * [entry] with `%f` on its `Exec` line, one `MimeType` line naming [mimeTypes], and a
     * `[Desktop Action]` group for each of [actions].
     */
    fun fixed(
        entry: String,
        mimeTypes: List<String>,
        actions: List<Action> = emptyList(),
    ): String {
        val lines = entry.lines()
        check(lines.any { it.startsWith("Exec=") }) { "The desktop entry has no Exec line:\n$entry" }

        val mimeLine = "MimeType=" + mimeTypes.joinToString(";", postfix = ";")
        val rewritten =
            lines.filterNot { it.startsWith("MimeType=") }.map { line ->
                when {
                    !line.startsWith("Exec=") -> line
                    FIELD_CODE.containsMatchIn(line) -> line
                    else -> "$line %f"
                }
            }

        val main = rewritten.dropLastWhile { it.isBlank() } + mimeLine
        if (actions.isEmpty()) return main.joinToString("\n", postfix = "\n")

        // The action runs the application's own command, without the field code that hands it a
        // file: an action is not given one.
        val command = lines.first { it.startsWith("Exec=") }.removePrefix("Exec=").replace(FIELD_CODE, "").trim()
        val groups =
            actions.flatMap { action ->
                listOf("", "[Desktop Action ${action.id}]", "Name=${action.name}", "Exec=$command ${action.arguments}")
            }

        return (main + "Actions=${actions.joinToString(";", postfix = ";") { it.id }}" + groups)
            .joinToString("\n", postfix = "\n")
    }

    /** Any of the Desktop Entry spec's file or URL field codes: one already there is left alone. */
    private val FIELD_CODE = Regex("""%[fFuU]""")

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "${command.joinToString(" ")} failed:\n$output" }
    }
}
