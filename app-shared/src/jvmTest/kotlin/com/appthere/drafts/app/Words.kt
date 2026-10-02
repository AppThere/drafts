package com.appthere.drafts.app

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * The English words for a string resource, for tests that assert on what is on screen.
 *
 * `appthere-drafts.md` 11.1 moved the strings into resources, and a resource is read either from a
 * composition or from a coroutine. A test asserting on a label has neither to hand at the moment it
 * needs the words, so it blocks for them -- which is what `runBlocking` is for and why the ban on
 * it (`engineering-conventions.md` 4.1) stops at the test source sets.
 *
 * Tests still assert the English on purpose. What they are checking is that the label on screen is
 * the one this code chose, and the only way to say which one that is, is to name it -- `Res.string`
 * on one side and the word on the other. A translation adds a file; it does not change what these
 * assert, because the default environment is still English.
 */
internal fun words(resource: StringResource): String = runBlocking { getString(resource) }
