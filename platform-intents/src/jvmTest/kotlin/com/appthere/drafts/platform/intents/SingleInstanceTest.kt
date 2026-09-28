package com.appthere.drafts.platform.intents

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 9.4's routing: "an already-running instance should open the document in a **new window**, not
 * launching a second process."
 *
 * Real sockets on the loopback interface. The claim is about what the operating system does when
 * two processes want the same port, so a fake would be asserting that the fake behaves as written.
 * A port of its own per test, well away from the real one, so a test never collides with an
 * application actually running on this machine.
 */
class SingleInstanceTest {
    private val instances = mutableListOf<SingleInstance>()

    @AfterTest
    fun release() {
        instances.forEach { it.release() }
    }

    @Test
    fun `the first launch becomes the running instance`() {
        assertTrue(instance(PORT).claim { }, "The first claim was refused")
    }

    @Test
    fun `a second launch does not`() {
        // The whole point: two processes, one application.
        instance(PORT + 1).claim { }

        assertFalse(instance(PORT + 1).claim { }, "Two instances both thought they were first")
    }

    @Test
    fun `a second launch hands its document to the first`() {
        val arrived = CountDownLatch(1)
        var received: String? = null
        instance(PORT + 2).claim { path ->
            received = path
            arrived.countDown()
        }

        assertTrue(instance(PORT + 2).handOff("/documents/chapter.md"), "The hand-off was refused")

        assertTrue(arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Nothing arrived")
        assertEquals("/documents/chapter.md", received)
    }

    @Test
    fun `several documents all arrive`() {
        // Opening three files at once from a file manager is three launches against one instance.
        val arrived = CountDownLatch(THREE)
        val received = mutableListOf<String>()
        instance(PORT + 3).claim { path ->
            synchronized(received) { received += path }
            arrived.countDown()
        }

        val handing = instance(PORT + 3)
        listOf("one.md", "two.md", "three.md").forEach { assertTrue(handing.handOff("/documents/$it")) }

        assertTrue(arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Only $received arrived")
        assertEquals(listOf("/documents/one.md", "/documents/three.md", "/documents/two.md"), received.sorted())
    }

    @Test
    fun `handing off to nobody fails rather than hanging`() {
        // The ordinary first launch. It has to find out quickly that it is alone, because nothing
        // can be shown until it does.
        assertFalse(instance(PORT + 4).handOff("/documents/chapter.md"))
    }

    @Test
    fun `a stranger on the port is not mistaken for the application`() {
        // A fixed port with no lock file means something else may be holding it. The greeting is
        // what stops this launch handing a document path to an unrelated service and exiting.
        val stranger = ServerSocket(PORT + 5, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching { stranger.accept().use { it.getOutputStream().write("SSH-2.0-OpenSSH\n".toByteArray()) } }
        }

        try {
            assertFalse(instance(PORT + 5).handOff("/documents/chapter.md"), "A document went to a stranger")
        } finally {
            stranger.close()
        }
    }

    @Test
    fun `a silent peer is not mistaken for the application either`() {
        // Something that accepts and says nothing -- a port scanner, or a process that died
        // between accepting and writing. The launch must give up and carry on rather than wait.
        val silent = ServerSocket(PORT + 6, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) { runCatching { silent.accept() } }

        try {
            assertFalse(instance(PORT + 6).handOff("/documents/chapter.md"))
        } finally {
            silent.close()
        }
    }

    @Test
    fun `releasing lets the next launch take over`() {
        // What happens when the running instance quits. Without this the port would stay claimed
        // for the lifetime of the machine and the application could never be started again.
        val first = instance(PORT + 7)
        first.claim { }

        first.release()

        assertTrue(instance(PORT + 7).claim { }, "The port was still held after release")
    }

    @Test
    fun `a connection that says nothing does not stop the instance listening`() {
        // One bad connection must not deafen the application for the rest of the session.
        val arrived = CountDownLatch(1)
        instance(PORT + 8).claim { arrived.countDown() }
        Socket().use { it.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), PORT + 8)) }

        assertTrue(instance(PORT + 8).handOff("/documents/after.md"), "The instance stopped listening")
        assertTrue(arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun instance(port: Int) = SingleInstance(port).also { instances += it }

    private companion object {
        /** Well clear of `SingleInstance.DEFAULT_PORT`, so tests never meet a real application. */
        const val PORT = 54_100
        const val THREE = 3
        const val TIMEOUT_SECONDS = 5L
    }
}
