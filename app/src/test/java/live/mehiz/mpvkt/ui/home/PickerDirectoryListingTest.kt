package live.mehiz.mpvkt.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerDirectoryListingTest {
  @Test
  fun `directories precede files with case insensitive alphabetical ordering`() {
    val entries = listOf(
      entry("z.mkv"),
      entry("B", true),
      entry("a.mp4"),
      entry("a", true),
    ).sortedWith(pickerEntryOrder)
    assertEquals(listOf("a", "B", "a.mp4", "z.mkv"), entries.map { it.name })
  }

  @Test
  fun `upper case media extensions remain visible`() {
    assertTrue(isVisiblePickerEntry("VIDEO.MP4", false))
    assertTrue(isVisiblePickerEntry("episode.MKV", false))
  }

  @Test
  fun `non media and hidden entries remain excluded`() {
    assertFalse(isVisiblePickerEntry("notes.txt", false))
    assertFalse(isVisiblePickerEntry(".video.mp4", false))
    assertFalse(isVisiblePickerEntry(".hidden", true))
    assertTrue(isVisiblePickerEntry("Videos", true))
  }

  @Test
  fun `metadata is retained in large sorted directory snapshots`() {
    val entries = (10000 downTo 1).map { index ->
      PickerEntryInfo("/videos/$index.mp4", "$index.mp4", false, index.toLong(), index * 1024L)
    }
    val sorted = entries.sortedWith(pickerEntryOrder)
    assertEquals(10000, sorted.size)
    assertEquals(entries.toSet(), sorted.toSet())
    assertTrue(sorted.zipWithNext().all { (left, right) -> pickerEntryOrder.compare(left, right) <= 0 })
  }

  private fun entry(name: String, directory: Boolean = false) =
    PickerEntryInfo("/videos/$name", name, directory, 0L, if (directory) null else 1024L)

  @Test
  fun `refresh drops deleted selections and preserves tap order`() {
    val entries = listOf(entry("a.mp4"), entry("b.mp4"), entry("c.mp4"))
    val selected = listOf("/videos/c.mp4", "/videos/deleted.mp4", "/videos/a.mp4")
    assertEquals(listOf("/videos/c.mp4", "/videos/a.mp4"), retainPickerSelection(selected, entries))
  }

  @Test
  fun `refresh clears selection when every selected file was removed`() {
    assertTrue(retainPickerSelection(listOf("/videos/deleted.mp4"), emptyList()).isEmpty())
  }

  @Test
  fun `a directory replacing a selected file is not kept in the queue`() {
    assertTrue(retainPickerSelection(listOf("/videos/a.mp4"), listOf(entry("a.mp4", true))).isEmpty())
  }
}
