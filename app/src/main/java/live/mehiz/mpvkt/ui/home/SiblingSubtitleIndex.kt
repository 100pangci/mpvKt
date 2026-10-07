package live.mehiz.mpvkt.ui.home

/** Prefix matching without scanning the entire directory for every video. */
internal class SiblingSubtitleIndex(files: List<Pair<String, String>>) {
  private data class Subtitle(val stem: String, val path: String, val order: Int)
  private val subtitles = files.mapIndexedNotNull { index, (name, path) ->
    if (name.substringAfterLast('.').lowercase() !in EXTENSIONS) return@mapIndexedNotNull null
    Subtitle(name.substringBeforeLast('.'), path, index)
  }.sortedBy { it.stem }

  fun forVideoName(name: String): List<String> {
    val prefix = name.substringBeforeLast('.')
    var low = 0
    var high = subtitles.size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (subtitles[middle].stem < prefix) low = middle + 1 else high = middle
    }
    val matches = ArrayList<Subtitle>()
    while (low < subtitles.size && subtitles[low].stem.startsWith(prefix)) {
      matches.add(subtitles[low++])
    }
    // Preserve the old directory/tap ordering for the default subtitle selection.
    return matches.sortedBy { it.order }.map { it.path }
  }

  companion object {
    private val EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub")
  }
}
