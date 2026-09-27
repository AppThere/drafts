package com.appthere.drafts.platform.files

import java.security.MessageDigest

/**
 * Android's SHA-256.
 *
 * Identical to the JVM actual, and separate from it because Android is a separate target rather
 * than because it needs different code. Coneal's `androidJvmMain` shared source set would remove
 * the duplication; the project does not have one, and inventing one for ten lines would be a
 * larger change than the duplication it saves.
 */
internal actual fun sha256Bytes(bytes: ByteArray): ByteArray = MessageDigest.getInstance(ALGORITHM).digest(bytes)

private const val ALGORITHM = "SHA-256"
