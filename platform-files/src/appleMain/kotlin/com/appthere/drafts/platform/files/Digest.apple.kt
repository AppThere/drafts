package com.appthere.drafts.platform.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.posix.uint8_tVar

/**
 * Apple's SHA-256, from CommonCrypto.
 *
 * Compiled but never run: nothing in this project has executed on an Apple target yet, and `check`
 * only compiles them. `DigestTest` lives in `commonTest`, so the day an Apple target does run, the
 * NIST vectors are already waiting for it.
 *
 * The empty input is passed as a null pointer rather than the address of element zero, because
 * there is no element zero to take the address of.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256Bytes(bytes: ByteArray): ByteArray =
    memScoped {
        val out = allocArray<uint8_tVar>(CC_SHA256_DIGEST_LENGTH)
        bytes.usePinned { pinned ->
            CC_SHA256(if (bytes.isEmpty()) null else pinned.addressOf(0), bytes.size.toUInt(), out)
        }
        ByteArray(CC_SHA256_DIGEST_LENGTH) { index -> out[index].toByte() }
    }
