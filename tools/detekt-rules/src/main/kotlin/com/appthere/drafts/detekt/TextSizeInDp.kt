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
 * Text sized in `dp` rather than `sp` (`engineering-conventions.md` 4.4).
 *
 * `dp` is a fixed physical size; `sp` scales with the reader's font-size preference. Text measured
 * in `dp` ignores that preference entirely, which is precisely the setting a reader with low
 * vision has already changed. `appthere-drafts.md` 10.2 requires the UI to hold up at 200% font
 * scale, and a `dp` text size means it cannot.
 *
 * The check is syntactic: a `fontSize`, `lineHeight` or `letterSpacing` argument whose expression
 * ends in `.dp`. It therefore also catches `TextStyle(fontSize = 16.dp)` and the various
 * `TextUnit` parameters, without needing type resolution.
 */
class TextSizeInDp(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "TextSizeInDp",
            severity = Severity.Defect,
            description =
                "Text dimensions must be expressed in sp so they honour the reader's " +
                    "font-size preference. dp ignores it.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        expression.valueArguments
            .filterIsInstance<KtValueArgument>()
            .forEach { reportIfTextSizedInDp(it) }
    }

    /**
     * The per-argument check, named rather than inlined into the loop above.
     *
     * The early returns read as "not this kind of argument"; as `continue` statements inside the
     * loop they read as control flow, which is what detekt's LoopWithTooManyJumpStatements was
     * pointing at.
     */
    private fun reportIfTextSizedInDp(argument: KtValueArgument) {
        // Each `takeIf` folds "is this the right kind of thing" into the lookup that produces it,
        // which keeps the guard count down and puts each condition next to the value it describes.
        val name =
            argument
                .getArgumentName()
                ?.asName
                ?.asString()
                ?.takeIf { it in TEXT_DIMENSION_PARAMETERS } ?: return

        val size =
            argument
                .getArgumentExpression()
                ?.text
                ?.trim()
                ?.takeIf { it.endsWith(DP_SUFFIX) } ?: return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(argument),
                message =
                    "'$name' is sized in dp ('$size'). Use sp so the value scales with " +
                        "the reader's font-size preference.",
            ),
        )
    }

    private companion object {
        const val DP_SUFFIX = ".dp"
        val TEXT_DIMENSION_PARAMETERS = setOf("fontSize", "lineHeight", "letterSpacing")
    }
}
