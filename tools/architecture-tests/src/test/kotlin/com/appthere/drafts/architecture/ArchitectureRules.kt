package com.appthere.drafts.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

/**
 * The architecture assertions from `engineering-conventions.md` 5, expressed as functions over a
 * scope rather than as test bodies.
 *
 * Separating them from the tests is what makes them verifiable. [ArchitectureTest] applies them to
 * the real modules, where -- in Phase 0 -- they have nothing to look at. [ArchitectureRulesFixtureTest]
 * applies the same functions to fixtures that deliberately violate them and asserts they fail.
 *
 * Without that second half, an assertion over an empty repository and an assertion that can never
 * fire look identical: both green. Phase 0's acceptance criteria ask for one of these to be shown
 * failing against a violating fixture for exactly that reason.
 */
object ArchitectureRules {
    /** Violations found by a rule. Empty means the rule holds. */
    data class Violations(
        val rule: String,
        val offenders: List<String>,
    ) {
        val holds: Boolean get() = offenders.isEmpty()

        fun describe(): String =
            if (holds) {
                "$rule: holds"
            } else {
                "$rule: ${offenders.size} violation(s)\n" + offenders.joinToString("\n") { "  - $it" }
            }
    }

    /**
     * `:core-model` is the root of the dependency graph and depends on nothing -- not on Compose
     * least of all. The IR is the one type everything else shares; coupling it to a UI toolkit
     * would drag Compose into the export backends and the parsers, which are headless.
     */
    fun coreModelDoesNotDependOnCompose(files: List<KoFileDeclaration>): Violations =
        Violations(
            rule = "core-model does not depend on Compose",
            offenders =
                files
                    .filter { file -> file.imports.any { it.name.startsWith("androidx.compose") } }
                    .map { it.path },
        )

    /**
     * `commonMain` cannot use JVM APIs: they do not exist on Kotlin/Native, so an import of
     * `java.*` in common code is a compile error on iOS and nowhere else. Catching it here means
     * finding out on the machine that wrote it rather than on the one macOS runner.
     */
    fun commonMainDoesNotUseJvmApis(files: List<KoFileDeclaration>): Violations =
        Violations(
            rule = "commonMain does not use JVM APIs",
            offenders =
                files
                    .filter { file ->
                        file.imports.any { it.name.startsWith("java.") || it.name.startsWith("javax.") }
                    }.map { it.path },
        )

    /**
     * `Dispatchers.IO` does not exist on native either (`engineering-conventions.md` 4.2,
     * "Multiplatform"). Dispatchers are injected, not reached for.
     */
    fun commonMainDoesNotUseDispatchersIo(files: List<KoFileDeclaration>): Violations =
        Violations(
            rule = "commonMain does not reference Dispatchers.IO",
            offenders =
                files
                    .filter { it.code.contains("Dispatchers.IO") }
                    .map { it.path },
        )

    /**
     * Only `:platform-files` touches the filesystem (`engineering-conventions.md` 4.2, "Files and
     * documents"). Every write to a user file has to go through the atomic temp-and-rename path
     * and the 8.2 digest check, and it can only be guaranteed to do so if there is exactly one
     * module that can write at all.
     */
    fun onlyPlatformFilesWritesToDisk(files: List<KoFileDeclaration>): Violations =
        Violations(
            rule = "only :platform-files writes to disk",
            offenders =
                files
                    .filterNot { it.path.contains("/platform-files/") }
                    .filter { file ->
                        file.imports.any { import -> FILE_IO_PACKAGES.any { import.name.startsWith(it) } }
                    }.map { it.path },
        )

    /**
     * Export backends use a real XML writer (`engineering-conventions.md` 4.2). The detekt rule
     * `drafts>XmlByStringConcatenation` catches this per-expression; this is the module-level net,
     * and it is the one that survives someone disabling the detekt rule for a file.
     */
    fun exportBackendsDoNotBuildXmlByConcatenation(files: List<KoFileDeclaration>): Violations =
        Violations(
            rule = "export backends do not build XML by concatenation",
            offenders =
                files
                    .filter { MARKUP_LITERAL.containsMatchIn(it.code) }
                    .map { it.path },
        )

    /**
     * `Modifier` is the first optional parameter of a composable that takes one.
     *
     * This is a convention with teeth: callers pass modifiers positionally after the required
     * arguments, and a composable that puts `Modifier` elsewhere silently accepts a modifier meant
     * for something else. compose-rules checks this too; having it here as well means it holds even
     * in modules where the compose-rules ruleset is not reached.
     */
    fun modifierIsFirstOptionalParameter(files: List<KoFileDeclaration>): Violations {
        val offenders =
            files.flatMap { file ->
                file
                    .functions()
                    .filter { function -> function.annotations.any { it.name == "Composable" } }
                    .filterNot { it.takesModifierFirstAmongOptionals() }
                    .map { "${file.path}: ${it.name}" }
            }

        return Violations("Modifier is the first optional parameter", offenders)
    }

    /**
     * The file's source with comments stripped.
     *
     * The two rules above match text rather than imports, because what they forbid is a spelling
     * rather than a dependency. Matching the raw text makes them fire on prose: a KDoc explaining
     * why `Dispatchers.IO` is banned violates the ban, which is how `:platform-files` first tripped
     * this rule -- the offending line was the comment saying the rule exists. A rule that punishes
     * writing about itself gets worked around rather than obeyed.
     *
     * String literals are deliberately kept. The XML rule is looking for markup *in a string*, so
     * stripping them would leave it matching nothing at all.
     */
    private val KoFileDeclaration.code: String get() = COMMENT.replace(text, " ")

    /**
     * Block comments and line comments. The lookbehind keeps `://` out of it, so a URL in a string
     * does not swallow the rest of its line.
     */
    private val COMMENT = Regex("""/\*[\s\S]*?\*/|(?<!:)//[^\n]*""")

    /** True when the function either takes no `Modifier`, or takes it as its first optional. */
    private fun KoFunctionDeclaration.takesModifierFirstAmongOptionals(): Boolean {
        val modifierIndex = parameters.indexOfFirst { it.type.name == "Modifier" }
        if (modifierIndex < 0) return true

        return modifierIndex == parameters.indexOfFirst { it.hasDefaultValue() }
    }

    private val FILE_IO_PACKAGES =
        listOf(
            "java.io.File",
            "java.nio.file",
            "kotlin.io.path",
            "okio",
            "platform.Foundation.NSFileManager",
        )

    /** A string literal opening an XML tag: `"<` followed by a name, slash, PI or declaration. */
    private val MARKUP_LITERAL = Regex(""""<[A-Za-z_/?!]""")
}
