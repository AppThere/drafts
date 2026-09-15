package com.appthere.drafts.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The five project-specific rules from `engineering-conventions.md` 4.1, 4.2 and 4.4.
 *
 * See the note in [FileLengthTest] for why the negative cases matter as much as the positive ones.
 */
class RunBlockingOutsideTestsTest {
    @Test
    fun `reports runBlocking`() {
        val code =
            """
            fun load(): String = runBlocking {
                fetch()
            }
            """.trimIndent()

        val findings = RunBlockingOutsideTests(Config.empty).lint(code)

        assertEquals(1, findings.size, "runBlocking in production code must be reported")
        assertTrue(
            findings.single().message.contains("blocks the calling thread"),
            "The message should explain why, not just name the call",
        )
    }

    @Test
    fun `does not report a suspend call or a launch`() {
        val code =
            """
            suspend fun load(): String = fetch()

            fun start(scope: CoroutineScope) {
                scope.launch { fetch() }
            }
            """.trimIndent()

        val findings = RunBlockingOutsideTests(Config.empty).lint(code)

        assertEquals(0, findings.size, "Correct coroutine usage must not be reported")
    }
}

class HardcodedUserFacingStringTest {
    @Test
    fun `reports a literal passed to Text`() {
        val code =
            """
            @Composable
            fun Title() {
                Text("Untitled document")
            }
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(1, findings.size, "A literal in Text() must be reported")
    }

    @Test
    fun `reports a literal contentDescription`() {
        val code =
            """
            @Composable
            fun Icon() {
                Image(painter = painter, contentDescription = "Export document")
            }
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(1, findings.size, "A literal contentDescription must be reported")
    }

    @Test
    fun `does not report a resource lookup`() {
        val code =
            """
            @Composable
            fun Title() {
                Text(stringResource(Res.string.untitled_document))
            }
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(0, findings.size, "The correct pattern must not be reported")
    }

    @Test
    fun `does not report an empty contentDescription`() {
        // contentDescription = "" is the documented way to mark an image decorative so screen
        // readers skip it. Flagging it would push people toward removing it, which is worse.
        val code =
            """
            @Composable
            fun Decoration() {
                Image(painter = painter, contentDescription = "")
            }
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(0, findings.size, "An intentionally empty description must not be reported")
    }

    @Test
    fun `does not report a Text node outside a composable`() {
        // :core-model's inline-text IR node is also called Text (export-pipeline.md's Document IR).
        // Without type resolution the rule sees only the short name, so it requires a @Composable
        // ancestor. Otherwise every parser test constructing IR would be a finding.
        val code =
            """
            fun buildIr(): List<Inline> = listOf(Text("plain literal"))
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(0, findings.size, "An IR node is not a UI string")
    }

    @Test
    fun `does not report IR parameters that share a name with UI ones`() {
        // :core-model has CodeBlock(text = ...), Link(title = ...) and FootnoteRef(label = ...).
        // Without type resolution the rule cannot tell those from Compose parameters by name
        // alone, which is exactly why it requires a @Composable ancestor.
        val code =
            """
            fun buildIr(): List<Block> =
                listOf(
                    CodeBlock(text = "fun main() {}", language = "kotlin"),
                    LinkReferenceDefinition(label = "ref", href = "/url", title = "A title"),
                )
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(0, findings.size, "IR construction is not UI text")
    }

    @Test
    fun `does not report IR construction inside a test class member function`() {
        // Mirrors the exact shape of a real :core-model test file, which this rule once flagged
        // three times. Kept as a regression test: the shape (class + @Test member + calls nested
        // in assertions and lambdas) is what the parent-walk has to see through.
        val code =
            """
            class BlockTreeTest {
                @Test
                fun `leaves have no children`() {
                    assertTrue(CodeBlock(text = "x").children.isEmpty())
                }

                @Test
                fun `a code span needs at least one backtick`() {
                    assertFailsWith<IllegalArgumentException> { CodeSpan(text = "x", backtickCount = 0) }
                }
            }
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(0, findings.size, "Findings: ${findings.map { it.message }}")
    }

    @Test
    fun `does not report a non user-facing argument`() {
        val code =
            """
            @Composable
            fun Row() {
                Box(modifier = Modifier.testTag("editor-root"))
            }
            """.trimIndent()

        val findings = HardcodedUserFacingString(Config.empty).lint(code)

        assertEquals(0, findings.size, "Test tags and similar are not user-facing")
    }
}

class TextSizeInDpTest {
    @Test
    fun `reports a font size in dp`() {
        val code =
            """
            val style = TextStyle(fontSize = 16.dp)
            """.trimIndent()

        val findings = TextSizeInDp(Config.empty).lint(code)

        assertEquals(1, findings.size, "fontSize in dp must be reported")
        assertTrue(
            findings.single().message.contains("sp"),
            "The message should name the correct unit",
        )
    }

    @Test
    fun `reports a line height in dp`() {
        val code =
            """
            val style = TextStyle(lineHeight = 24.dp)
            """.trimIndent()

        val findings = TextSizeInDp(Config.empty).lint(code)

        assertEquals(1, findings.size, "lineHeight in dp must be reported")
    }

    @Test
    fun `does not report a font size in sp`() {
        val code =
            """
            val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
            """.trimIndent()

        val findings = TextSizeInDp(Config.empty).lint(code)

        assertEquals(0, findings.size, "sp is the correct unit and must not be reported")
    }

    @Test
    fun `does not report a non-text dimension in dp`() {
        // dp is correct for everything that is not type. Flagging padding would make the rule
        // useless noise.
        val code =
            """
            val modifier = Modifier.padding(all = 16.dp).size(width = 48.dp, height = 48.dp)
            """.trimIndent()

        val findings = TextSizeInDp(Config.empty).lint(code)

        assertEquals(0, findings.size, "Layout dimensions in dp are correct")
    }
}

class DirectionalPaddingTest {
    @Test
    fun `reports a left argument`() {
        val code =
            """
            val modifier = Modifier.padding(left = 8.dp)
            """.trimIndent()

        val findings = DirectionalPadding(Config.empty).lint(code)

        assertEquals(1, findings.size, "A left argument must be reported")
        assertTrue(
            findings.single().message.contains("start"),
            "The message should name the replacement",
        )
    }

    @Test
    fun `reports absolutePadding`() {
        val code =
            """
            val modifier = Modifier.absolutePadding(8.dp, 0.dp, 8.dp, 0.dp)
            """.trimIndent()

        val findings = DirectionalPadding(Config.empty).lint(code)

        assertEquals(1, findings.size, "absolutePadding opts out of direction awareness")
    }

    @Test
    fun `does not report start and end`() {
        val code =
            """
            val modifier = Modifier.padding(start = 8.dp, end = 16.dp)
            """.trimIndent()

        val findings = DirectionalPadding(Config.empty).lint(code)

        assertEquals(0, findings.size, "start/end is the correct form")
    }
}

class XmlByStringConcatenationTest {
    @Test
    fun `reports an opening tag in a string literal`() {
        val code =
            """
            fun write(title: String) = "<w:p><w:t>" + title + "</w:t></w:p>"
            """.trimIndent()

        val findings = XmlByStringConcatenation(Config.empty).lint(code)

        assertTrue(findings.isNotEmpty(), "Hand-built markup must be reported")
    }

    @Test
    fun `reports an xml declaration`() {
        val code =
            """
            const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            """.trimIndent()

        val findings = XmlByStringConcatenation(Config.empty).lint(code)

        assertEquals(1, findings.size, "An XML declaration built as a literal must be reported")
    }

    @Test
    fun `does not report a string containing a comparison`() {
        // The rule must distinguish markup from an angle bracket used as punctuation, or it will
        // fire on error messages and be suppressed everywhere.
        val code =
            """
            val message = "expected a < b"
            val arrow = "a -> b"
            """.trimIndent()

        val findings = XmlByStringConcatenation(Config.empty).lint(code)

        assertEquals(0, findings.size, "A comparison is not markup")
    }
}
