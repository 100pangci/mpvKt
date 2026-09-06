package live.mehiz.mpvkt.ui.utils

/**
 * Case-insensitive natural order: digit runs compare numerically, so EP2
 * sorts before EP10. Used to keep the playlist order identical to what a
 * viewer expects from the directory listing.
 */
object NaturalOrderComparator : Comparator<String> {
  override fun compare(a: String, b: String): Int {
    var i = 0
    var j = 0
    var result = 0
    while (i < a.length && j < b.length && result == 0) {
      val ca = a[i]
      val cb = b[j]
      if (ca.isDigit() && cb.isDigit()) {
        val endA = digitRunEnd(a, i)
        val endB = digitRunEnd(b, j)
        result = a.substring(i, endA).toLong().compareTo(b.substring(j, endB).toLong())
        if (result == 0) {
          // Same value with different padding: fewer leading zeros first.
          result = (endA - i).compareTo(endB - j)
        }
        i = endA
        j = endB
      } else {
        result = ca.lowercaseChar().compareTo(cb.lowercaseChar())
        i++
        j++
      }
    }
    return if (result != 0) result else (a.length - i).compareTo(b.length - j)
  }

  private fun digitRunEnd(s: String, start: Int): Int {
    var end = start
    while (end < s.length && s[end].isDigit()) end++
    return end
  }
}
