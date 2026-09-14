package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Returns true when the function carries a `@Composable` annotation.
 *
 * Matched on the short name only. Type resolution would let us confirm it is
 * `androidx.compose.runtime.Composable` specifically, but detekt's type-resolved tasks are not
 * wired for Kotlin Multiplatform source sets in Phase 0, and no other annotation in this codebase
 * is called `Composable`.
 */
internal fun KtNamedFunction.isComposable(): Boolean =
    annotationEntries.any { it.shortName?.asString() == "Composable" }

/**
 * Counts the lines of a function body, excluding blank lines and the braces themselves.
 */
internal fun KtNamedFunction.bodyLineCount(): Int {
    val body = bodyBlockExpression ?: bodyExpression ?: return 0
    return body.text
        .lineSequence()
        .map { it.trim() }
        .count { it.isNotEmpty() && it != "{" && it != "}" }
}

/**
 * Composable function length, per `engineering-conventions.md` 2: 60 warn, 100 fail.
 *
 * 2.1 is the reasoning: a screen-level composable nests `Scaffold` -> `LazyColumn` -> `items` ->
 * block composable, and each level adds a brace. Applying the generic 40/60 function limit to
 * that shape produces constant false positives, "which trains everyone -- human and agent -- to
 * ignore the tool. That's a worse outcome than having no tool."
 *
 * So the generic `complexity>LongMethod` rule excludes `@Composable` via `ignoreAnnotated`, and
 * composables are held to these larger numbers instead. They are held to *something*: excluding
 * them from the generic rule without this one would mean composables had no length limit at all.
 */
class LongComposableFunction(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "LongComposableFunction",
            severity = Severity.Maintainability,
            description =
                "Composable function is longer than the threshold in " +
                    "engineering-conventions.md 2. Extract a cohesive child composable with a name that " +
                    "describes what it renders.",
            debt = Debt.TWENTY_MINS,
        )

    private val maxLines: Int get() = valueOrDefault(MAX_LINES, DEFAULT_MAX_LINES)

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isComposable()) return

        val lines = function.bodyLineCount()
        if (lines > maxLines) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(function),
                    message =
                        "Composable '${function.name}' is $lines lines, exceeding the limit " +
                            "of $maxLines.",
                ),
            )
        }
    }

    private companion object {
        const val MAX_LINES = "maxLines"
        const val DEFAULT_MAX_LINES = 100
    }
}

/**
 * Composable parameter count, per `engineering-conventions.md` 2: 7 warn, 10 fail.
 *
 * State hoisting legitimately widens these signatures -- a composable that hoists all of its state
 * takes every piece of it as a parameter, plus a lambda per event. That is the pattern working as
 * intended, not a smell, which is why the limit is higher than the generic 5/7.
 *
 * Trailing lambdas and `Modifier` count like any other parameter: the number that matters for
 * readability is how many things a caller has to supply.
 */
class ComposableParameterList(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "ComposableParameterList",
            severity = Severity.Maintainability,
            description =
                "Composable takes more parameters than the threshold in " +
                    "engineering-conventions.md 2. Consider grouping related state into a single type.",
            debt = Debt.TWENTY_MINS,
        )

    private val maxParameters: Int get() = valueOrDefault(MAX_PARAMETERS, DEFAULT_MAX_PARAMETERS)

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isComposable()) return

        val count = function.valueParameters.size
        if (count > maxParameters) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(function),
                    message =
                        "Composable '${function.name}' takes $count parameters, exceeding " +
                            "the limit of $maxParameters.",
                ),
            )
        }
    }

    private companion object {
        const val MAX_PARAMETERS = "maxParameters"
        const val DEFAULT_MAX_PARAMETERS = 10
    }
}
