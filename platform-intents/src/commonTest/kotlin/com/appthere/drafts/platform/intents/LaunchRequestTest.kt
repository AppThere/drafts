package com.appthere.drafts.platform.intents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a launch asks for, from its arguments and on its way to a running instance (7.4, 9.4). */
class LaunchRequestTest {
    @Test
    fun `a launch with nothing to ask for asks for nothing`() {
        // A first launch like this restores the reader's sessions (7.3).
        assertNull(LaunchRequest.of(emptyList()))
    }

    @Test
    fun `a path asks for that file`() {
        assertEquals(LaunchRequest.Open("/documents/chapter.md"), LaunchRequest.of(listOf("/documents/chapter.md")))
    }

    @Test
    fun `the launcher's entry points ask for a new document of their kind`() {
        assertEquals(LaunchRequest.New(DocumentKind.Fountain), LaunchRequest.of(listOf("--new", "fountain")))
        assertEquals(LaunchRequest.New(DocumentKind.Markdown), LaunchRequest.of(listOf("--new", "markdown")))
    }

    @Test
    fun `a new document of no stated kind is the kind last created`() {
        assertEquals(LaunchRequest.New(kind = null), LaunchRequest.of(listOf("--new")))
        assertEquals(LaunchRequest.New(kind = null), LaunchRequest.of(listOf("--new", "sonnet")))
    }

    @Test
    fun `every request survives the trip to a running instance`() {
        listOf(
            LaunchRequest.Open("/documents/chapter 3.md"),
            LaunchRequest.New(DocumentKind.Fountain),
            LaunchRequest.New(kind = null),
        ).forEach { assertEquals(it, LaunchRequest.decoded(it.encoded())) }
    }

    @Test
    fun `a path travels exactly as it always did`() {
        // An older running instance reads a bare path; a newer one must still send one.
        assertEquals("/documents/new chapter.md", LaunchRequest.Open("/documents/new chapter.md").encoded())
    }

    @Test
    fun `a file whose name looks like a request is still a file`() {
        // "new markdown" is a legal file name. Only the NUL prefix, which no path can hold, means new.
        assertEquals(LaunchRequest.Open("new markdown"), LaunchRequest.decoded("new markdown"))
    }
}
