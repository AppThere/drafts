package com.appthere.drafts.platform.files

import kotlin.jvm.JvmInline

/**
 * A SHA-256 digest of a document's bytes, as `appthere-drafts.md` 8.2 requires.
 *
 * "At open, record `baseDigest` (SHA-256 of file contents), size, and mtime. **Before any write to
 * the user's file**, re-read the file and recompute the digest. If it differs from `baseDigest`,
 * **do not write**."
 *
 * Held as a value class over the hex string rather than a `ByteArray` so that comparing two
 * digests is `==` and means what it looks like. `ByteArray` equality is identity, and a digest
 * check that silently compared references would report every file as changed -- or, if someone
 * "fixed" it the other way, none of them.
 */
@JvmInline
value class Digest(
    val hex: String,
) {
    init {
        require(hex.length == HEX_LENGTH) { "A SHA-256 digest is $HEX_LENGTH hex characters, got ${hex.length}" }
    }

    /** The `sha256:…` spelling 7.3 uses when a digest is written into a session file. */
    override fun toString(): String = "$PREFIX$hex"

    companion object {
        const val PREFIX = "sha256:"
        const val HEX_LENGTH = 64

        /** Parses the spelling [toString] produces, with or without the prefix. */
        fun parse(text: String): Digest = Digest(text.removePrefix(PREFIX))
    }
}

/**
 * Computes the SHA-256 of [bytes].
 *
 * `expect`/`actual` rather than a Kotlin implementation in this module: every target already ships
 * a vetted one, and a hand-written hash is a thing that can be subtly wrong in a way its own tests
 * agree with. `DigestTest` checks the actuals against the published NIST vectors, which is the
 * check that would catch that.
 *
 * The actuals return raw bytes and the hex spelling happens here, once. Three copies of a
 * byte-to-hex loop are three chances to drop a leading zero on one platform only -- a bug that
 * would show up as a document that every device but one thought had changed.
 */
fun sha256(bytes: ByteArray): Digest = Digest(sha256Bytes(bytes).toHex())

/** The platform's SHA-256, unformatted. */
internal expect fun sha256Bytes(bytes: ByteArray): ByteArray

private fun ByteArray.toHex(): String =
    joinToString("") { byte ->
        byte
            .toInt()
            .and(BYTE_MASK)
            .toString(HEX_RADIX)
            .padStart(HEX_PER_BYTE, '0')
    }

/** `Byte` is signed, so a byte above 0x7F sign-extends to a negative `Int` without this. */
private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_PER_BYTE = 2
