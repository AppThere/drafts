package com.appthere.drafts.platform.intents

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import kotlin.concurrent.thread

/**
 * One running application per user, however many times it is launched.
 *
 * `appthere-drafts.md` 9.4: "Route to an existing instance via a single-instance lock and a local
 * socket or named pipe rather than launching a second process", and "an already-running instance
 * should open the document in a **new window**, not replace the current one."
 *
 * A Unix-domain socket at [address], which is in a folder only this user can reach (the caller's
 * business -- see `desktopInstanceAddress` in `:platform-files`). That makes the instance one *per
 * user*: a second person signed in to the same machine has their own, and cannot hand documents to
 * this one. A loopback TCP port could not promise either. Every user shared it, so one user's
 * running instance stopped another's from starting at all, and anything on the machine could ask
 * it to open files. Java supports these sockets on Linux and macOS, and on Windows 10 and later.
 *
 * The socket is the lock: binding is an atomic claim the operating system arbitrates. What binding
 * a file cannot do is clean up after a process that was killed -- `SnapshotKillTest` is a reminder
 * that processes here do get killed -- so a socket file nobody answers at is [removeStale]d and
 * claimed. Removing it is the filesystem's business, and `engineering-conventions.md` 4.2 reserves
 * that to `:platform-files`; the caller passes the means in.
 *
 * The greeting still guards the conversation. The server speaks first and the client sends nothing
 * until it recognises what it heard, so the worst anything unexpected at the address costs is a
 * launch that decides it is the first instance.
 */
class SingleInstance(
    private val address: String,
    private val removeStale: (String) -> Unit,
) {
    private var server: ServerSocketChannel? = null

    /**
     * Becomes the running instance, or reports that one already exists.
     *
     * [onMessage] is called on a background thread for each line a later launch hands over -- a
     * [LaunchRequest], encoded; this class carries it without knowing what it means. Whatever
     * it does with it has to be safe from there -- on the desktop that means posting to the
     * composition rather than touching it.
     */
    fun claim(onMessage: (String) -> Unit): Boolean {
        val bound = bind() ?: return false

        server = bound
        thread(isDaemon = true, name = "drafts-single-instance") { accept(bound, onMessage) }
        return true
    }

    /**
     * Hands [message] to the running instance. False when there is nobody to hand it to.
     *
     * False is also what anything that is not the application gets, and what a timeout gets. In
     * every one of those cases the right answer is the same: carry on as the first instance.
     * Refusing to start because something unexpected answered would make the application
     * unlaunchable for a reason the reader could not possibly diagnose.
     */
    fun handOff(message: String): Boolean =
        try {
            SocketChannel.open(socketAddress()).use { channel ->
                channel.configureBlocking(false)
                if (channel.readLine(TIMEOUT_MILLIS) != GREETING) {
                    false
                } else {
                    channel.writeLine(message)
                    true
                }
            }
        } catch (expectedWhenNobodyIsListening: IOException) {
            // The ordinary first launch: there is nothing to hand off to, so this one is the instance.
            false
        }

    /**
     * Stops listening. The socket file goes, so the next launch becomes the running instance.
     *
     * Only the instance that claimed the address removes it. A launch that handed off and is on its
     * way out must not take the running instance's socket with it.
     */
    @Synchronized
    fun release() {
        val listening = server ?: return
        runCatching { listening.close() }
        server = null
        runCatching { removeStale(address) }
    }

    /**
     * Claims [address], clearing a socket file left by an instance that is no longer there.
     *
     * Only when nothing answers. Something that does answer is either the application or something
     * else entirely, and in neither case is the file this launch's to remove.
     */
    private fun bind(): ServerSocketChannel? = bindOnce() ?: takeOverStale()

    private fun takeOverStale(): ServerSocketChannel? {
        if (answers()) return null

        runCatching { removeStale(address) }
        return bindOnce()
    }

    private fun bindOnce(): ServerSocketChannel? {
        val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        return try {
            channel.bind(socketAddress(), BACKLOG)
        } catch (expectedWhenTaken: IOException) {
            channel.close()
            null
        }
    }

    private fun answers(): Boolean =
        try {
            SocketChannel.open(socketAddress()).use { true }
        } catch (expectedWhenStale: IOException) {
            false
        }

    private fun accept(
        bound: ServerSocketChannel,
        onMessage: (String) -> Unit,
    ) {
        while (bound.isOpen) {
            val handled = runCatching { bound.accept().use { greet(it, onMessage) } }

            // A failed connection is one launch that did not get through, not a reason to stop
            // listening -- the application would then silently stop accepting documents for the
            // rest of the session.
            if (handled.isFailure && !bound.isOpen) return
        }
    }

    private fun greet(
        channel: SocketChannel,
        onMessage: (String) -> Unit,
    ) {
        channel.configureBlocking(false)
        channel.writeLine(GREETING)

        // Something that connected and said nothing -- a launch that died between connecting and
        // writing -- times out to null. It is not a message.
        val message = channel.readLine(TIMEOUT_MILLIS)
        if (!message.isNullOrBlank()) onMessage(message)
    }

    private fun socketAddress() = UnixDomainSocketAddress.of(address)

    companion object {
        const val GREETING = "appthere-drafts 1"

        private const val BACKLOG = 8
        private const val TIMEOUT_MILLIS = 2_000L
    }
}

/**
 * Reads one line, or gives up at the deadline.
 *
 * Unix-domain channels have no read timeout of their own, so the wait is a [Selector]'s. A peer that
 * never finishes its line must not hold a launch -- or the instance's only listening thread -- for
 * longer than that.
 */
private fun SocketChannel.readLine(timeoutMillis: Long): String? {
    val line = LineBuffer()
    val deadline = System.currentTimeMillis() + timeoutMillis

    Selector.open().use { selector ->
        register(selector, SelectionKey.OP_READ)
        var done = false
        while (!done) {
            val left = deadline - System.currentTimeMillis()
            done = left <= 0 || line.tooLong
            if (!done && selector.select(left) > 0) {
                selector.selectedKeys().clear()
                done = line.readFrom(this)
            }
        }
    }
    return line.finished
}

/** The bytes of one line as they arrive, and the line once it has. */
private class LineBuffer {
    private val bytes = ByteArrayOutputStream()
    private val chunk = ByteBuffer.allocate(READ_CHUNK)

    /** The whole line, or what there was of it when the peer went away; null until then. */
    var finished: String? = null
        private set

    /** Past any request's length: whatever is talking is not the application. */
    val tooLong: Boolean get() = bytes.size() > MAX_LINE_BYTES

    /** Takes what [channel] has; true once the line is complete or the peer has gone. */
    fun readFrom(channel: SocketChannel): Boolean {
        chunk.clear()
        val ended = channel.read(chunk) < 0
        chunk.flip()

        var complete = false
        while (chunk.hasRemaining() && !complete) {
            val byte = chunk.get()
            if (byte == NEWLINE) complete = true else bytes.write(byte.toInt())
        }

        val text = bytes.toString(Charsets.UTF_8)
        finished = if (complete) text else text.takeIf { ended && it.isNotEmpty() }
        return complete || ended
    }
}

/** Writes one line; a request is small enough that a non-blocking channel takes it whole. */
private fun SocketChannel.writeLine(text: String) {
    val bytes = ByteBuffer.wrap((text + "\n").toByteArray(Charsets.UTF_8))
    while (bytes.hasRemaining()) write(bytes)
}

private const val READ_CHUNK = 512

/** Far longer than any path or request; anything past it is not the application talking. */
private const val MAX_LINE_BYTES = 64 * 1024
private const val NEWLINE = '\n'.code.toByte()
