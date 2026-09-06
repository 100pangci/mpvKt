package live.mehiz.mpvkt.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistNodeDisplayNameTest {

  @Test
  fun `real path keeps only the file name`() {
    assertEquals("Episode 01.mp4", PlaylistNode("/storage/emulated/0/Movies/Series/Episode 01.mp4").displayName)
    assertEquals("a+b.mp4", PlaylistNode("/storage/emulated/0/a+b.mp4").displayName)
  }

  @Test
  fun `SAF content uri decodes percent escapes and drops the document path`() {
    assertEquals(
      "电影 01.mp4",
      PlaylistNode(
        "content://com.android.externalstorage.documents/tree/primary%3ADownload" +
          "/document/primary%3ADownload%2F%E7%94%B5%E5%BD%B1%2001.mp4",
      ).displayName,
    )
    assertEquals(
      "Episode 01.mp4",
      PlaylistNode(
        "content://com.android.externalstorage.documents/document/primary%3AMovies%2FEpisode%2001.mp4",
      ).displayName,
    )
  }

  @Test
  fun `SAF encoded separator folds into a plain one`() {
    assertEquals("a.mp4", PlaylistNode(".../document/primary%3AFolder%2Fa.mp4").displayName)
  }

  @Test
  fun `network url drops credentials query and fragment`() {
    assertEquals(
      "Movie 01.mp4",
      PlaylistNode("http://user:pass@192.168.1.2:5244/dav/Movie%2001.mp4?token=x#frag").displayName,
    )
    assertEquals("movie.mp4", PlaylistNode("ftp://anonymous:@ftp.example.com/media/movie.mp4").displayName)
  }

  @Test
  fun `title wins over the parsed file name`() {
    assertEquals("My Title", PlaylistNode("http://h/x.mp4", title = "My Title").displayName)
  }

  @Test
  fun `blank fallback shows the raw string`() {
    assertEquals("/", PlaylistNode("/").displayName)
  }
}
