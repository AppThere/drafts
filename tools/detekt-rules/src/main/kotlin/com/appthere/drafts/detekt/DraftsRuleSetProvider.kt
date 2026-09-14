package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * The project's own detekt rules, covering the parts of `engineering-conventions.md` that the
 * stock ruleset cannot express.
 *
 * Three of these exist because detekt 1.23.8 has no equivalent rule at all: file length, and the
 * separate composable thresholds from 2.1. The other five are the project-specific antipatterns
 * named in 4.1, 4.2 and 4.4.
 *
 * Every rule here has a test that deliberately violates it and asserts it fires. That is not
 * ceremony: a custom rule that silently never matches looks exactly like a codebase with no
 * violations, and the difference does not surface for months.
 */
class DraftsRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = RULE_SET_ID

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                // Size thresholds detekt does not ship (engineering-conventions.md 2)
                FileLength(config),
                LongComposableFunction(config),
                ComposableParameterList(config),
                // Project-specific antipatterns (engineering-conventions.md 4.1, 4.2, 4.4)
                RunBlockingOutsideTests(config),
                HardcodedUserFacingString(config),
                TextSizeInDp(config),
                DirectionalPadding(config),
                XmlByStringConcatenation(config),
            ),
        )

    internal companion object {
        const val RULE_SET_ID: String = "drafts"
    }
}
