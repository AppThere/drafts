package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * A `TextStyle` built outside `:design-system`.
 *
 * A bare `TextStyle` names no font family, so the text it styles is set in the platform's default
 * face rather than 5.1's Atkinson Hyperlegible -- which is how the whole interface ended up in one
 * typeface and the document in another. `proseStyleOf` styles the document and `interfaceTextStyle`
 * everything around it; both live in `:design-system`, which is excluded in configuration so that
 * those two can build the style they hand out.
 *
 * Syntactic: a call to `TextStyle(...)`. Copying an existing style (`style.copy(...)`) and reading
 * `TextStyle.Default` are not constructions and are not reported.
 */
class TextStyleOutsideDesignSystem(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "TextStyleOutsideDesignSystem",
            severity = Severity.Style,
            description =
                "Text styles come from the design system, so every piece of text is set in the " +
                    "bundled typeface. A bare TextStyle falls back to the platform's default face.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeExpression?.text != TEXT_STYLE) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message =
                    "A TextStyle built here has no typeface of its own and shows in the platform's " +
                        "default. Use interfaceTextStyle() for interface text or proseStyleOf() for the document.",
            ),
        )
    }

    private companion object {
        const val TEXT_STYLE = "TextStyle"
    }
}
