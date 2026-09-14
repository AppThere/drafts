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
 * `runBlocking` outside tests (`engineering-conventions.md` 4.1).
 *
 * In production code `runBlocking` parks a thread until the coroutine finishes. On the main
 * dispatcher that is a frozen UI; off it, it is a thread leaked from a bounded pool. Neither
 * failure is visible in review, and both are visible to the user.
 *
 * In tests it is ordinary and correct, so test source sets are excluded **by path, in
 * `config/detekt/detekt.yml`**, the same mechanism detekt's own rules use for this. The exclusion
 * lives in config rather than in this rule so that it is visible in the same place as every other
 * exemption, per 2 -- "an exemption is a config entry with a comment explaining why".
 */
class RunBlockingOutsideTests(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "RunBlockingOutsideTests",
            severity = Severity.Defect,
            description =
                "runBlocking blocks the calling thread. Use a suspend function, or launch " +
                    "in a scope with an injected dispatcher.",
            debt = Debt.TWENTY_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeExpression?.text ?: return
        if (callee != RUN_BLOCKING) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message =
                    "runBlocking blocks the calling thread and must not appear in " +
                        "production code. Make the caller suspend, or inject a dispatcher.",
            ),
        )
    }

    private companion object {
        const val RUN_BLOCKING = "runBlocking"
    }
}
