package live.mehiz.mpvkt.player

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AssFontParserTest {

  @get:Rule
  val folder = TemporaryFolder()

  private fun write(content: String): File = folder.newFile("test.ass").apply { writeText(content) }

  @Test
  fun `parses fontnames from v4plus style lines`() {
    val ass = """
      [Script Info]
      Title: test

      [V4+ Styles]
      Format: Name, Fontname, Fontsize, PrimaryColour, Bold, Outline
      Style: Default,FOT-TsukuCOldMin Pr6N L,60,&H00FFFFFF,0,2
      Style: OP,HYXuanSong 35S,80,&H00FFFFFF,1,3
    """.trimIndent()
    assertEquals(listOf("FOT-TsukuCOldMin Pr6N L", "HYXuanSong 35S"), parseAssFontNames(write(ass)))
  }

  @Test
  fun `fontname column follows the format line`() {
    val ass = """
      [V4+ Styles]
      Format: Name, MarginL, MarginR, Fontname, Fontsize
      Style: A,10,10,HYXuanSong 55S,60
    """.trimIndent()
    assertEquals(listOf("HYXuanSong 55S"), parseAssFontNames(write(ass)))
  }

  @Test
  fun `skips sections before the style table`() {
    val ass = """
      [Events]
      Format: Layer, Start, End, Style, Text
      Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello

      [V4+ Styles]
      Format: Name, Fontname, Fontsize
      Style: Default,Some Font,60
    """.trimIndent()
    assertEquals(listOf("Some Font"), parseAssFontNames(write(ass)))
  }

  @Test
  fun `supports legacy v4 header`() {
    val ass = """
      [V4 Styles]
      Format: Name, Fontname, Fontsize, PrimaryColour
      Style: Default,Old Font,60,&H00FFFFFF
    """.trimIndent()
    assertEquals(listOf("Old Font"), parseAssFontNames(write(ass)))
  }

  @Test
  fun `returns empty list when no style table exists`() {
    val ass = """
      [Script Info]
      Title: nothing here
    """.trimIndent()
    assertEquals(emptyList<String>(), parseAssFontNames(write(ass)))
  }

  @Test
  fun `deduplicates repeated families`() {
    val ass = """
      [V4+ Styles]
      Format: Name, Fontname, Fontsize
      Style: A,Same Font,60
      Style: B,Same Font,40
    """.trimIndent()
    assertEquals(listOf("Same Font"), parseAssFontNames(write(ass)))
  }
}
