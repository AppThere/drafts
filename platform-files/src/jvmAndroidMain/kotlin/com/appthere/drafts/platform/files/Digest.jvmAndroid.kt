package com.appthere.drafts.platform.files

import java.security.MessageDigest

/**
 * The JVM's SHA-256, from the platform's own provider.
 *
 * Shared with Android, which is the same JVM. This used to be two identical files; the duplication
 * existed only because there was nowhere to put code both targets share.
 */
internal actual fun sha256Bytes(bytes: ByteArray): ByteArray = MessageDigest.getInstance(ALGORITHM).digest(bytes)

/** Every JRE is required to provide this one, so the lookup cannot fail. */
private const val ALGORITHM = "SHA-256"
