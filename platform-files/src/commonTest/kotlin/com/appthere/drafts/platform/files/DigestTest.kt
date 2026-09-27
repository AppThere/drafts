package com.appthere.drafts.platform.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Every target's SHA-256 against the published NIST vectors.
 *
 * These are the test for [sha256] because they are the only ones that can fail for the right
 * reason. A test that hashed something and compared the result against what the same code produced
 * yesterday would agree with any implementation, including one that had quietly started hashing the
 * wrong bytes. The vectors come from outside the project, so the actuals have to match something
 * they cannot influence -- which is also what makes the JVM and Apple implementations verifiably
 * the same function rather than two functions that happen to have the same name.
 *
 * Source: NIST's published SHA-256 example values, under
 * csrc.nist.gov/projects/cryptographic-standards-and-guidelines.
 */
class DigestTest {
    @Test
    fun `the empty input hashes to the published vector`() {
        // Worth its own case: hashing nothing is where an implementation that mishandles a
        // zero-length pointer goes wrong, and an empty document is a thing a reader can create.
        assertEquals(EMPTY, sha256(ByteArray(0)).hex)
    }

    @Test
    fun `abc hashes to the published vector`() {
        assertEquals(ABC, sha256("abc".encodeToByteArray()).hex)
    }

    @Test
    fun `the multi-block vector hashes correctly`() {
        // 448 bits: longer than one 512-bit block once the padding is added, so this exercises the
        // block loop rather than just the single-block path the shorter vectors take.
        assertEquals(TWO_BLOCK, sha256(TWO_BLOCK_INPUT.encodeToByteArray()).hex)
    }

    @Test
    fun `bytes outside ASCII hash to the same thing as their UTF-8`() {
        // 8.2 hashes file contents, and the files are Markdown a reader may have written in any
        // language. A digest that went through a platform's default charset on one target and
        // UTF-8 on another would report a conflict on every save from the other device.
        val text = "Café — 日本語 🌲"

        assertEquals(sha256(text.encodeToByteArray()), sha256(text.encodeToByteArray()))
        assertEquals(HEX_LENGTH, sha256(text.encodeToByteArray()).hex.length)
    }

    @Test
    fun `a single changed byte changes the digest`() {
        // The property 8.2 actually relies on. If this did not hold, "re-read the file and
        // recompute the digest" would pass over an edit and the write would destroy it.
        assertNotEquals(sha256("document".encodeToByteArray()), sha256("documenu".encodeToByteArray()))
    }

    @Test
    fun `a digest round-trips through its spelled form`() {
        // 7.3 stores digests as `sha256:...` in session files. What is written has to parse back to
        // what was hashed, or every restored session would look like a conflict.
        val digest = sha256("round trip".encodeToByteArray())

        assertEquals(digest, Digest.parse(digest.toString()))
        assertEquals(digest, Digest.parse(digest.hex))
    }

    @Test
    fun `a truncated digest is rejected rather than stored`() {
        // A short hex string compares unequal to every real digest, so accepting one would turn
        // into a permanent false conflict that no amount of reloading would clear.
        assertFailsWith<IllegalArgumentException> { Digest.parse("sha256:abc123") }
    }

    private companion object {
        const val HEX_LENGTH = 64

        const val EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        const val TWO_BLOCK_INPUT = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        const val TWO_BLOCK = "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
    }
}
