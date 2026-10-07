package live.mehiz.mpvkt.ui.player

// MIME type describes the content, not which Intent field contains its URI.
internal fun chooseIntentMediaSource(data: String?, stream: String?, text: String?): String? =
  data?.takeIf { it.isNotBlank() }
    ?: stream?.takeIf { it.isNotBlank() }
    ?: text?.trim()?.takeIf { it.isNotBlank() }
