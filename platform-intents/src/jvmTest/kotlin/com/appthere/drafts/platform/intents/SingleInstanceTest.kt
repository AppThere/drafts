package com.appthere.drafts.platform.intents

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 9.4's routing: "an already-running instance should open the document in a **new window**, not
 * launching a second process" -- one instance per user.
 *
 * Real Unix-domain sockets in a temporary folder. The claim is about what the operating system does
 * when two processes want the same address, so a fake would be asserting that the fake behaves as
 * written. Each test has a folder of its own, so a test never meets an application actually running
 * on this machine.
 */
class SingleInstanceTest {
    private val folder: Path = createTempDirectory("drafts-instance")
    private val instances = mutableListOf<SingleInstance>()

    @AfterTest
    fun release() {
        instances.forEach { it.release() }
        folder.toFile().deleteRecursively()
    }

    @Test
    fun `the first launch becomes the running instance`() {
        assertTrue(instance().claim { }, "The first claim was refused")
    }

    @Test
    fun `a second launch does not`() {
        // The whole point: two processes, one application.
        instance().claim { }

        assertFalse(instance().claim { }, "Two instances both thought they were first")
    }

    @Test
    fun `a second launch hands its request to the first`() {
        val arrived = CountDownLatch(1)
        var received: String? = null
        instance().claim { message ->
            received = message
            arrived.countDown()
        }

        assertTrue(instance().handOff("/documents/chapter.md"), "The hand-off was refused")

        assertTrue(arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Nothing arrived")
        assertEquals("/documents/chapter.md", received)
    }

    @Test
    fun `several requests all arrive`() {
        // Opening three files at once from a file manager is three launches against one instance.
        val arrived = CountDownLatch(THREE)
        val received = mutableListOf<String>()
        instance().claim { message ->
            synchronized(received) { received += message }
            arrived.countDown()
        }

        val handing = instance()
        listOf("one.md", "two.md", "three.md").forEach { assertTrue(handing.handOff("/documents/$it")) }

        assertTrue(arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Only $received arrived")
        assertEquals(listOf("/documents/one.md", "/documents/three.md", "/documents/two.md"), received.sorted())
    }

    @Test
    fun `handing off to nobody fails rather than hanging`() {
        // The ordinary first launch. It has to find out quickly that it is alone, because nothing
        // can be shown until it does.
        assertFalse(instance().handOff("/documents/chapter.md"))
    }

    @Test
    fun `something else at the address is not mistaken for the application`() {
        // The greeting is what stops a launch handing a request to whatever else is listening and
        // then exiting.
        val stranger = listen()
        thread(isDaemon = true) {
            runCatching { stranger.accept().use { it.write(ByteBuffer.wrap("SSH-2.0-OpenSSH\n".toByteArray())) } }
        }

        try {
            assertFalse(instance().handOff("/documents/chapter.md"), "A request went to a stranger")
        } finally {
            stranger.close()
        }
    }

    @Test
    fun `a silent peer is not mistaken for the application either`() {
        // Something that accepts and says nothing. The launch must give up and carry on rather than
        // wait for ever.
        val silent = listen()
        thread(isDaemon = true) { runCatching { silent.accept() } }

        try {
            assertFalse(instance().handOff("/documents/chapter.md"))
        } finally {
            silent.close()
        }
    }

    @Test
    fun `releasing lets the next launch take over`() {
        // What happens when the running instance quits.
        val first = instance()
        first.claim { }

        first.release()

        assertTrue(instance().claim { }, "The address was still held after release")
    }

    @Test
    fun `a socket left by a killed instance does not stop the next launch`() {
        // kill -9 leaves the socket file behind, with nothing listening at it. Without clearing it,
        // the application could not start again until someone deleted a file they have never heard
        // of.
        listen().close()
        assertTrue(address().exists(), "The test did not leave a socket file behind")

        assertTrue(instance().claim { }, "A dead instance's socket file blocked the launch")
    }

    @Test
    fun `a socket something is listening at is never cleared`() {
        // Only a file nobody answers at is stale. Something that answers is not this launch's to
        // remove, whatever it is.
        val other = listen()
        thread(isDaemon = true) { runCatching { while (true) other.accept().close() } }

        try {
            assertFalse(instance().claim { }, "A live socket was taken over")
        } finally {
            other.close()
        }
    }

    @Test
    fun `a launch that only handed off leaves the running instance's socket alone`() {
        // A second launch releases on its way out. It must not take the running one's address with
        // it, or the launch after that would start a second application.
        val running = instance()
        running.claim { }
        val second = instance()
        second.handOff("/documents/chapter.md")

        second.release()

        assertFalse(instance().claim { }, "The running instance lost its address")
    }

    @Test
    fun `a connection that says nothing does not stop the instance listening`() {
        // One bad connection must not deafen the application for the rest of the session.
        val arrived = CountDownLatch(1)
        instance().claim { arrived.countDown() }
        SocketChannel.open(UnixDomainSocketAddress.of(address())).close()

        assertTrue(instance().handOff("/documents/after.md"), "The instance stopped listening")
        assertTrue(arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun address(): Path = folder.resolve("drafts.sock")

    private fun instance() =
        SingleInstance(address().toString()) { Files.deleteIfExists(Path.of(it)) }.also { instances += it }

    /** Something other than the application, listening at the address. */
    private fun listen(): ServerSocketChannel =
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(address()))

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val THREE = 3
    }
}
