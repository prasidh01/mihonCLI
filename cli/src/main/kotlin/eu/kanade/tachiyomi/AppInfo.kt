package eu.kanade.tachiyomi

object AppInfo {
    fun getVersionCode(): Int = 100

    fun getVersionName(): String = "0.17.0"

    fun getSupportedImageMimeTypes(): List<String> = listOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/avif",
        "image/jxl",
    )
}
