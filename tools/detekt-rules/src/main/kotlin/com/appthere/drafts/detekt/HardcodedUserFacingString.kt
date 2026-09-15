package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Hardcoded user-facing string (`engineering-conventions.md` 4.4, `appthere-drafts.md` 11.1).
 *
 * Fires on a string literal passed to `Text(`, or to a `contentDescription =` or title argument,
 * **inside a `@Composable`**. Those are where user-visible text enters the UI, and a literal there
 * cannot be translated, cannot be adjusted for a screen reader, and cannot be changed without a
 * rebuild.
 *
 * Deliberately narrow, in two directions. The `@Composable` requirement is explained at the call
 * site below -- in short, the same parameter names appear all over `:core-model` and flagging them
 * would make the rule noise. And within a composable it looks only at the arguments that carry
 * user-visible text, not at every literal, because test tags, log keys and format specifiers are
 * all legitimately literal.
 *
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

        // The whole rule is scoped to composables, and that is load-bearing rather than cautious.
        //
        // Without type resolution this rule sees short names only, and every name it looks for is
        // also an ordinary parameter name elsewhere in this codebase: :core-model has
        // `CodeBlock(text = ...)`, `Link(title = ...)`, `FootnoteRef(label = ...)` and an inline IR
        // node called `Text`. A rule that flagged those would be suppressed within a week, and a
        // suppressed rule catches nothing. A hardcoded *user-facing* string is by definition in UI
        // code, so requiring a @Composable ancestor is not a narrowing of intent -- it is the
        // intent.
        //
        // Checked once per call rather than per argument: it is a property of the enclosing
        // function, not of any one argument.
        if (!expression.isInsideComposable()) return

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
        val isUserFacing =
            argumentName in USER_FACING_PARAMETERS ||
                (argumentName == null && callee in TEXT_COMPOSABLES && isFirstArgument)

        if (!isUserFacing) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(literal),
                message = buildMessage(callee, argumentName),
            ),
        )
    }

    /** True when the nearest enclosing function is annotated `@Composable`. */
    private fun KtCallExpression.isInsideComposable(): Boolean =
        getStrictParentOfType<KtNamedFunction>()
            ?.annotationEntries
            ?.any { it.shortName?.asString() == COMPOSABLE } == true

    private fun buildMessage(
        callee: String?,
        argumentName: String?,
    ): String {
        val where = argumentName?.let { "'$it'" } ?: "the text argument of '$callee'"
        return "Hardcoded user-facing string passed as $where. Move it to a string resource."
    }

    private companion object {
        const val COMPOSABLE = "Composable"
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
