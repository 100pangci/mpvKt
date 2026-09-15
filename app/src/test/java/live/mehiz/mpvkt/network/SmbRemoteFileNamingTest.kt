package live.mehiz.mpvkt.network

import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.SmbFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Properties

/**
 * Guards the jcifs-ng path assembly: keeping the trailing slash on every
 * directory is what stops subfolders from inheriting the share name
 * ("mediaMovies") and what makes `getName()` return the leaf only.
 */
class SmbRemoteFileNamingTest {

  private val context = BaseContext(PropertyConfiguration(Properties()))
  private val serverUrl = "smb://nas:445/"

  @Test
  fun `a share root keeps its trailing slash`() {
    val file = build("/media", "")
    assertEquals("media/", file.name)
    assertEquals("/media/", file.path.removePrefix("smb://nas:445"))
    assertEquals("\\", file.uncPath)
  }

  @Test
  fun `a folder under a share does not inherit the share name`() {
    val file = build("/media/Movies", "S1")
    assertEquals("S1/", file.name)
    assertEquals("/media/Movies/S1/", file.path.removePrefix("smb://nas:445"))
    assertEquals("\\Movies\\S1\\", file.uncPath)
  }

  @Test
  fun `a share picked from the share list browses by itself`() {
    val file = build("", "media/Movies")
    assertEquals("Movies/", file.name)
    assertEquals("\\Movies\\", file.uncPath)
  }

  @Test
  fun `a file keeps a bare final segment`() {
    val file = build("/media/Movies", "S1/ep1.mkv", directory = false)
    assertEquals("ep1.mkv", file.name)
    assertEquals("/media/Movies/S1/ep1.mkv", file.path.removePrefix("smb://nas:445"))
    assertEquals("\\Movies\\S1\\ep1.mkv", file.uncPath)
  }

  @Test
  fun `special characters survive naming`() {
    val file = build("/media", "My #1 (final) 电影.mkv", directory = false)
    assertEquals("My #1 (final) 电影.mkv", file.name)
    assertEquals("\\My #1 (final) 电影.mkv", file.uncPath)
  }

  private fun build(basePath: String, path: String, directory: Boolean = true): SmbFile {
    val target = requireNotNull(SmbRemotePath.resolve(basePath, path)) { "no share in $basePath/$path" }
    return SmbRemote.buildFile(context, serverUrl, target, directory)
  }
}
