package com.appthere.drafts.platform.intents

import java.io.BufferedReader
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * One running application, however many times it is launched.
 *
 * `appthere-drafts.md` 9.4 on Windows: "Route to an existing instance via a single-instance lock
 * and a local socket or named pipe rather than launching a second process." And for all three
 * desktops: "an already-running instance should open the document in a **new window**, not replace
 * the current one."
 *
 * The socket *is* the lock. Binding a port is an atomic claim the operating system arbitrates, so
 * there is no lock file to go stale when a process is killed -- and `SnapshotKillTest` is a
 * reminder that processes here do get killed. A lock file would also have meant a second module
 * touching the filesystem, which `engineering-conventions.md` 4.2 reserves to `:platform-files`.
 *
 * Loopback only. [InetAddress.getLoopbackAddress] is not a configuration choice: bound to any
 * interface this would accept a document path from anything on the network and open it.
 *
 * The greeting is what stops a stray service on the same port being mistaken for the application.
 * The server speaks first and the client sends nothing until it recognises what it heard, so the
 * worst a collision costs is a launch that decides it is the first instance.
 */
class SingleInstance(
    private val port: Int = DEFAULT_PORT,
) {
    private var server: ServerSocket? = null

    /**
     * Becomes the running instance, or reports that one already exists.
     *
     * [onOpen] is called on a background thread for each path a later launch hands over. Whatever
     * it does with it has to be safe from there -- on the desktop that means posting to the
     * composition rather than touching it.
     */
    fun claim(onOpen: (String) -> Unit): Boolean {
        val bound =
            try {
                ServerSocket(port, BACKLOG, InetAddress.getLoopbackAddress())
            } catch (expectedWhenAlreadyRunning: IOException) {
                // The ordinary second launch. Somebody else holds the port, which is the answer.
                return false
            }

        server = bound
        thread(isDaemon = true, name = "drafts-single-instance") { accept(bound, onOpen) }
        return true
    }

    /**
     * Hands [path] to the running instance. False when there is nobody to hand it to.
     *
     * False is also what a stranger on the port gets, and what a timeout gets. In every one of
     * those cases the right answer is the same: carry on as the first instance. Refusing to start
     * because something unexpected answered would make the application unlaunchable for a reason
     * the reader could not possibly diagnose.
     */
    fun handOff(path: String): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MILLIS)
                socket.soTimeout = TIMEOUT_MILLIS

                val reader = socket.getInputStream().bufferedReader()
                if (reader.readLine() != GREETING) {
                    false
                } else {
                    socket.getOutputStream().write((path + "\n").toByteArray())
                    socket.getOutputStream().flush()
                    true
                }
            }
        } catch (expectedWhenNobodyIsListening: IOException) {
            // The ordinary first launch: there is nothing to hand off to, so this one is the instance.
            false
        }

    /** Stops listening. The port is released, so the next launch becomes the running instance. */
    fun release() {
        runCatching { server?.close() }
        server = null
    }

    private fun accept(
        bound: ServerSocket,
        onOpen: (String) -> Unit,
    ) {
        while (!bound.isClosed) {
            val handled = runCatching { bound.accept().use { greet(it, onOpen) } }

            // A failed connection is one launch that did not get through, not a reason to stop
            // listening -- the application would then silently stop accepting documents for the
            // rest of the session.
            if (handled.isFailure && bound.isClosed) return
        }
    }

    private fun greet(
        socket: Socket,
        onOpen: (String) -> Unit,
    ) {
        socket.soTimeout = TIMEOUT_MILLIS
        socket.getOutputStream().write((GREETING + "\n").toByteArray())
        socket.getOutputStream().flush()

        val path = readPath(socket.getInputStream().bufferedReader())
        if (!path.isNullOrBlank()) onOpen(path)
    }

    private fun readPath(reader: BufferedReader): String? =
        try {
            reader.readLine()
        } catch (expectedWhenPeerSaysNothing: SocketTimeoutException) {
            // Something connected and said nothing: a port scanner, or a launch that died between
            // connecting and writing. Neither is a document.
            null
        }

    companion object {
        /**
         * Chosen once and fixed, because both sides have to agree on it without a file to share.
         *
         * In the IANA dynamic range, so nothing is registered here, and specific enough to this
         * application that a collision is a coincidence rather than a pattern. The greeting is
         * what makes a collision harmless.
         */
        const val DEFAULT_PORT = 51_317

        const val GREETING = "appthere-drafts 1"

        private const val BACKLOG = 8
        private const val TIMEOUT_MILLIS = 2_000
    }
}
