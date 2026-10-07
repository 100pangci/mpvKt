package live.mehiz.mpvkt.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class PlaybackQueueStoreTest {
  @get:Rule
  val temporary = TemporaryFolder(File(System.getProperty("java.io.tmpdir"), "opencode").apply { mkdirs() })

  @Test
  fun `large queue crosses the activity boundary as a constant size token`() {
    val directory = temporary.newFolder()
    val paths = (1..4000).map { "content://videos/document/" + "long%20folder/".repeat(20) + "episode-$it.mp4" }
    val request = PlaybackQueueRequest(paths, paths.associateWith { listOf("$it.ass", "$it.zh.srt") })
    val token = PlaybackQueueStore(directory).save(request)
    assertEquals(36, token.length)
    assertTrue(File(directory, "$token.json").length() > 1024 * 1024)
    // Reconstructing a store simulates a fresh process: no in-memory singleton is required.
    assertEquals(request, PlaybackQueueStore(directory).load(token))
  }

  @Test
  fun `special file names and subtitle order survive serialization`() {
    val store = PlaybackQueueStore(temporary.newFolder())
    val path = "/videos/中文 日本語 #100% \"quoted\"\nvideo.mp4"
    val request = PlaybackQueueRequest(listOf(path), mapOf(path to listOf("b.ass", "a.srt")))
    assertEquals(request, store.load(store.save(request)))
  }

  @Test
  fun `requests remain independent and can be restored repeatedly`() {
    val store = PlaybackQueueStore(temporary.newFolder())
    val first = PlaybackQueueRequest(listOf("first.mp4"))
    val second = PlaybackQueueRequest(listOf("second.mp4"))
    val a = store.save(first)
    val b = store.save(second)
    assertEquals(first, store.load(a))
    assertEquals(second, store.load(b))
    assertEquals(first, store.load(a))
  }

  @Test
  fun `untrusted tokens cannot escape the private store`() {
    val store = PlaybackQueueStore(temporary.newFolder())
    assertNull(store.load("../private-file"))
    assertNull(store.load("/absolute/path"))
    assertNull(store.load(UUID.randomUUID().toString()))
  }

  @Test
  fun `incomplete or corrupt saved requests fail safely`() {
    val directory = temporary.newFolder()
    val token = UUID.randomUUID().toString()
    File(directory, "$token.json").writeText("{invalid")
    assertNull(PlaybackQueueStore(directory).load(token))
  }

  @Test
  fun `expired requests are cleaned without removing fresh requests`() {
    val directory = temporary.newFolder()
    val store = PlaybackQueueStore(directory)
    val old = store.save(PlaybackQueueRequest(listOf("old.mp4")))
    val oldFile = File(directory, "$old.json")
    assertTrue(oldFile.setLastModified(1L))
    val fresh = store.save(PlaybackQueueRequest(listOf("fresh.mp4")))
    assertFalse(oldFile.exists())
    assertEquals(listOf("fresh.mp4"), store.load(fresh)?.paths)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `empty queues are rejected`() {
    PlaybackQueueStore(temporary.newFolder()).save(PlaybackQueueRequest(emptyList()))
  }

  @Test
  fun `concurrent request preparation safely creates and shares the store directory`() {
    val directory = File(temporary.newFolder(), "queues")
    val executor = Executors.newFixedThreadPool(4)
    try {
      val requests = (1..20).map { PlaybackQueueRequest(listOf("video-$it.mp4")) }
      val tokens = executor.invokeAll(requests.map { request -> Callable { PlaybackQueueStore(directory).save(request) } })
        .map { it.get() }
      tokens.zip(requests).forEach { (token, request) -> assertEquals(request, PlaybackQueueStore(directory).load(token)) }
    } finally {
      executor.shutdownNow()
    }
  }
}
