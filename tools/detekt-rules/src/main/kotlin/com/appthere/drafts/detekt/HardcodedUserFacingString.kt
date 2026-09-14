package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Hardcoded user-facing string (`engineering-conventions.md` 4.4, `appthere-drafts.md` 11.1).
 *
 * Fires on a string literal passed to `Text(`, or to a `contentDescription =` or title argument.
 * Those three are where user-visible text actually enters the UI, and a literal there cannot be
 * translated, cannot be adjusted for a screen reader, and cannot be changed without a rebuild.
 *
 * Deliberately narrow. A rule that flagged every string literal in a composable would fire on test
 * tags, log keys and format specifiers, and would be suppressed everywhere within a week.
 * Interpolated templates are allowed through because a template that reads a resource and
 * substitutes a value -- `"$count words"` built from a resource -- is the correct pattern, and the
 * literal parts of it are the resource's problem, not this rule's.
 */
class HardcodedUserFacingString(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "HardcodedUserFacingString",
            severity = Severity.Maintainability,
            description =
                "User-facing text must come from string resources so it can be translated " +
                    "and adjusted for assistive technology.",
            debt = Debt.TEN_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeExpression?.text
        val arguments = expression.valueArguments.filterIsInstance<KtValueArgument>()

        arguments.forEach { argument ->
            reportIfHardcoded(argument, callee, isFirstArgument = argument == arguments.firstOrNull())
        }
    }

    /** The per-argument check, named rather than inlined, so the loop carries no control flow. */
    private fun reportIfHardcoded(
        argument: KtValueArgument,
        callee: String?,
        isFirstArgument: Boolean,
    ) {
        // Two exclusions fold into the lookup itself. An interpolated template is composing a
        // resource with a value, which is correct; an empty literal is the documented way to mark
        // an image decorative so screen readers skip it.
        val literal =
            (argument.getArgumentExpression() as? KtStringTemplateExpression)
                ?.takeIf { !it.hasInterpolation() && it.entries.isNotEmpty() } ?: return

        val argumentName = argument.getArgumentName()?.asName?.asString()
        val isPositionalTextArgument =
            argumentName == null && callee in TEXT_COMPOSABLES && isFirstArgument

        if (!isPositionalTextArgument && argumentName !in USER_FACING_PARAMETERS) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(literal),
                message = buildMessage(callee, argumentName),
            ),
        )
    }

    private fun buildMessage(
        callee: String?,
        argumentName: String?,
    ): String {
        val where = argumentName?.let { "'$it'" } ?: "the text argument of '$callee'"
        return "Hardcoded user-facing string passed as $where. Move it to a string resource."
    }

    private companion object {
        val TEXT_COMPOSABLES = setOf("Text", "BasicText")
        val USER_FACING_PARAMETERS =
            setOf(
                "text",
                "contentDescription",
                "title",
                "label",
                "placeholder",
                "stateDescription",
            )
    }
}
