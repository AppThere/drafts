package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * XML built by string concatenation inside the export backends
 * (`engineering-conventions.md` 4.2, `export-pipeline.md`).
 *
 * Scoped by path to `:core-export-*` in `config/detekt/detekt.yml`. The export backends are
 * write-only -- nothing reads the output back -- so a subtly malformed document is not caught by
 * any round trip. It is caught by the user, in Word, later.
 *
 * Hand-built XML gets escaping wrong for exactly the content this app produces: an ampersand or an
 * angle bracket in someone's prose, a smart quote, an emoji outside the BMP. It also gets OOXML's
 * element ordering wrong, which the schema enforces and which concatenation cannot express.
 *
 * The fix is never "escape more carefully" -- it is to use the real XML writer in
 * `:core-export-container`, which is what that module exists for.
 *
 * Matching is restricted to literals that look like markup (`<` followed by a name, `/`, `?` or
 * `!`), so that a string containing a comparison or an arrow is not flagged.
 */
class XmlByStringConcatenation(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "XmlByStringConcatenation",
            severity = Severity.Defect,
            description =
                "Export backends must build XML with a real writer, not string " +
                    "concatenation. Concatenation gets escaping and element ordering wrong.",
            debt = Debt.TWENTY_MINS,
        )

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)

        val text = expression.text ?: return
        if (!MARKUP.containsMatchIn(text)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message =
                    "String literal contains XML markup. Use the XML writer in " +
                        ":core-export-container instead of building markup by hand.",
            ),
        )
    }

    private companion object {
        /** `<` followed by a tag name, a closing slash, a PI, or a declaration. */
        val MARKUP = Regex("""<[A-Za-z_/?!]""")
    }
}
