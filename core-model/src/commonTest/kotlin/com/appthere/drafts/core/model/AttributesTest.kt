package com.appthere.drafts.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Merge semantics, which implement specific sentences of `markdown-dialect.md` 7. The rest of
 * [Attributes] is a data class and asserting a data class returns what you put in it would be one
 * of the plausible-looking tests AGENTS.md 4 warns about.
 */
class AttributesTest {
    @Test
    fun `an explicit id overrides a generated one`() {
        // markdown-dialect.md 7: "An explicit {#id} overrides the generated one." Heading ids are
        // generated from the text first, then the author's attribute block merges over the top.
        val generated = Attributes(id = "heading-text")
        val explicit = Attributes(id = "custom-id")

        assertEquals("custom-id", (generated + explicit).id)
    }

    @Test
    fun `a generated id survives when the author set no id`() {
        val generated = Attributes(id = "heading-text")
        val authorClassesOnly = Attributes(classes = listOf("highlight"))

        val merged = generated + authorClassesOnly

        assertEquals("heading-text", merged.id)
        assertEquals(listOf("highlight"), merged.classes)
    }

    @Test
    fun `classes append rather than replace`() {
        // 7 says `.class` *appends* a class, which is why this is not a simple overwrite.
        val left = Attributes(classes = listOf("rounded"))
        val right = Attributes(classes = listOf("shadow"))

        assertEquals(listOf("rounded", "shadow"), (left + right).classes)
    }

    @Test
    fun `duplicate classes are collapsed`() {
        // A class list is a set in every output format, so emitting one twice is never right.
        val left = Attributes(classes = listOf("highlight", "rounded"))
        val right = Attributes(classes = listOf("rounded", "shadow"))

        assertEquals(listOf("highlight", "rounded", "shadow"), (left + right).classes)
    }

    @Test
    fun `later key values win`() {
        val left = Attributes(keyValues = mapOf("width" to "400", "loading" to "lazy"))
        val right = Attributes(keyValues = mapOf("width" to "800"))

        val merged = left + right

        assertEquals("800", merged["width"])
        assertEquals("lazy", merged["loading"], "Keys the right side did not mention are kept")
    }

    @Test
    fun `merging with empty changes nothing`() {
        val attrs = Attributes(id = "x", classes = listOf("a"), keyValues = mapOf("k" to "v"))

        assertEquals(attrs, attrs + Attributes.EMPTY)
        assertEquals(attrs, Attributes.EMPTY + attrs)
    }

    @Test
    fun `isEmpty is true only when nothing is set`() {
        assertTrue(Attributes.EMPTY.isEmpty)
        assertFalse(Attributes(id = "x").isEmpty)
        assertFalse(Attributes(classes = listOf("x")).isEmpty)
        assertFalse(Attributes(keyValues = mapOf("k" to "v")).isEmpty)
    }

    @Test
    fun `lookups miss cleanly`() {
        val attrs = Attributes(classes = listOf("present"))

        assertNull(attrs["absent"])
        assertTrue(attrs.hasClass("present"))
        assertFalse(attrs.hasClass("absent"))
    }
}
