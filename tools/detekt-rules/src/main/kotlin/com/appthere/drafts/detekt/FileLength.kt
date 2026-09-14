package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile

/**
 * File length, per `engineering-conventions.md` 2: 400 warn, 600 fail.
 *
 * detekt 1.23.8 ships no file-length rule -- `LargeClass` measures a class body, not a file --
 * so this is ours. The threshold comes from config, which is how the same rule serves both the
 * warn and the fail run.
 *
 * Counted **excluding imports, licence header and blank lines**, as 2 specifies. Counting raw
 * lines would make the limit depend on how many imports a file happens to need, which is not a
 * property anyone should be managing.
 *
 * 1 is worth re-reading before acting on a finding from this rule: a limit is a signal to
 * reconsider the file's cohesion, not an instruction to cut it in half. `EditorStateUtils2.kt`
 * is a worse outcome than the file that triggered this.
 */
class FileLength(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "FileLength",
            severity = Severity.Maintainability,
            description =
                "File is longer than the threshold in engineering-conventions.md 2. " +
                    "Reconsider whether the file is doing one thing; do not split it at an arbitrary seam.",
            debt = Debt.TWENTY_MINS,
        )

    private val maxLines: Int get() = valueOrDefault(MAX_LINES, DEFAULT_MAX_LINES)

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)

        val significant = countSignificantLines(file.text)
        if (significant > maxLines) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(file),
                    message =
                        "File has $significant significant lines, exceeding the limit of " +
                            "$maxLines. Imports, the licence header and blank lines are not counted.",
                ),
            )
        }
    }

    /**
     * Lines that count toward the limit: not blank, not an import, not the package declaration,
     * and not part of the licence header.
     */
    private fun countSignificantLines(text: String): Int =
        text
            .lineSequence()
            .map { it.trim() }
            .toList()
            .dropLicenceHeader()
            .count { it.isNotEmpty() && !it.startsWith(IMPORT_PREFIX) && !it.startsWith(PACKAGE_PREFIX) }

    /**
     * Drops the block comment that opens the file, if there is one.
     *
     * Separating this from the counting is what keeps both simple: done in one pass it needs two
     * pieces of carried state and a branch per line, which is how the first version of this
     * function crossed the complexity and nesting warn thresholds.
     */
    private fun List<String>.dropLicenceHeader(): List<String> {
        val firstContent = indexOfFirst { it.isNotEmpty() }
        if (firstContent < 0 || !this[firstContent].startsWith(BLOCK_COMMENT_START)) return this

        val closing = drop(firstContent).indexOfFirst { it.contains(BLOCK_COMMENT_END) }
        return if (closing < 0) emptyList() else drop(firstContent + closing + 1)
    }

    private companion object {
        const val MAX_LINES = "maxLines"
        const val BLOCK_COMMENT_START = "/*"
        const val BLOCK_COMMENT_END = "*/"
        const val IMPORT_PREFIX = "import "
        const val PACKAGE_PREFIX = "package "
        const val DEFAULT_MAX_LINES = 600
    }
}
