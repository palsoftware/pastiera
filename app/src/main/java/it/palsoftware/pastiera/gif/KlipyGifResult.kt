package it.palsoftware.pastiera.gif

enum class KlipyMediaType(
    val apiPathSegment: String,
    val displayName: String,
    val singularName: String
) {
    GIF("gifs", "GIFs", "GIF"),
    STICKER("stickers", "Stickers", "sticker"),
    LOCAL("local", "Local", "image")
}

data class KlipyGifResult(
    val id: String,
    val title: String,
    val mediaType: KlipyMediaType,
    val previewUrl: String,
    val gifUrl: String,
    val mimeType: String,
    val shareUrl: String,
    val isLocal: Boolean = false
)
