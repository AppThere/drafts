package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * `left`/`right` layout instead of `start`/`end` (`engineering-conventions.md` 4.4,
 * `appthere-drafts.md` 11.2).
 *
 * In a right-to-left locale `start` is on the right. Code written in terms of `left` and `right`
 * produces a layout that is mirrored everywhere except the places someone hardcoded, which is
 * worse than no RTL support at all -- it looks deliberate.
 *
 * This matters more here than in most apps: Drafts is a *writing* tool, and 11.2 commits to
 * bidirectional text in the editing surface itself. A mirrored gutter with an unmirrored caret is
 * not a cosmetic bug for someone drafting in Arabic or Hebrew.
 *
 * Two shapes are caught: an argument literally named `left` or `right`, and a call to Compose's
 * `absolutePadding`, whose entire purpose is to opt out of direction awareness.
 */
class DirectionalPadding(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "DirectionalPadding",
            severity = Severity.Defect,
            description =
                "Layout must use start/end so it mirrors in right-to-left locales. " +
                    "left/right does not.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeExpression?.text

        if (callee in ABSOLUTE_FUNCTIONS) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message =
                        "'$callee' is direction-unaware by design. Use padding(start =, " +
                            "end =) so the layout mirrors in right-to-left locales.",
                ),
            )
            return
        }

        expression.valueArguments
            .filterIsInstance<KtValueArgument>()
            .forEach { reportIfPhysicalDirection(it) }
    }

    /** The per-argument check, named rather than inlined, so the loop carries no control flow. */
    private fun reportIfPhysicalDirection(argument: KtValueArgument) {
        val name = argument.getArgumentName()?.asName?.asString() ?: return
        if (name !in DIRECTIONAL_PARAMETERS) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(argument),
                message =
                    "'$name =' is a physical direction. Use '${REPLACEMENTS[name]} =' " +
                        "so the layout mirrors in right-to-left locales.",
            ),
        )
    }

    private companion object {
        val ABSOLUTE_FUNCTIONS = setOf("absolutePadding", "absoluteOffset")
        val REPLACEMENTS = mapOf("left" to "start", "right" to "end")
        val DIRECTIONAL_PARAMETERS = REPLACEMENTS.keys
    }
}
