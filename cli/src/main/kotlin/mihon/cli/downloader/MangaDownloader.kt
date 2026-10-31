package mihon.cli.downloader

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class MangaDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val concurrency: Int = 3,
) {

    suspend fun downloadChapter(
        source: Source,
        manga: SManga,
        chapter: SChapter,
        destinationDir: File,
        asCbz: Boolean = true,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val safeMangaTitle = sanitizeFileName(manga.title.ifBlank { "Unknown Manga" })
        val safeChapterName = sanitizeFileName(chapter.name.ifBlank { "Chapter ${chapter.chapter_number}" })

        val mangaDir = File(destinationDir, safeMangaTitle)
        mangaDir.mkdirs()

        val finalTarget = if (asCbz) {
            File(mangaDir, "$safeChapterName.cbz")
        } else {
            File(mangaDir, safeChapterName)
        }

        if (finalTarget.exists()) {
            if (asCbz && finalTarget.length() > 1024) {
                // Already downloaded
                return@withContext finalTarget
            } else if (!asCbz && finalTarget.isDirectory && (finalTarget.list()?.isNotEmpty() == true)) {
                return@withContext finalTarget
            }
        }

        // Temporary directory for downloading raw images
        val tempDir = File(mangaDir, ".tmp-$safeChapterName-${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            val pages = source.getPageList(chapter)
            if (pages.isEmpty()) {
                throw IllegalStateException("Source returned 0 pages for chapter '${chapter.name}'")
            }

            val total = pages.size
            val completed = AtomicInteger(0)
            val semaphore = Semaphore(concurrency)

            val tasks = pages.mapIndexed { index, page ->
                async {
                    semaphore.withPermit {
                        downloadPageWithRetry(source, page, index + 1, tempDir)
                        val curr = completed.incrementAndGet()
                        onProgress?.invoke(curr, total)
                    }
                }
            }

            tasks.awaitAll()

            val resultFile = if (asCbz) {
                CbzPackager.pack(tempDir, finalTarget, manga, chapter)
                tempDir.deleteRecursively()
                finalTarget
            } else {
                if (finalTarget.exists()) finalTarget.deleteRecursively()
                tempDir.renameTo(finalTarget)
                finalTarget
            }

            return@withContext resultFile
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
    }

    private suspend fun downloadPageWithRetry(
        source: Source,
        page: Page,
        pageNumber: Int,
        outputDir: File,
        maxRetries: Int = 3,
    ) {
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                // Ensure imageUrl is available
                var imgUrl = page.imageUrl
                if (imgUrl.isNullOrBlank() && source is HttpSource) {
                    imgUrl = source.getImageUrl(page)
                    page.imageUrl = imgUrl
                }

                val (bytes, contentType) = fetchImageBytes(source, page, imgUrl)
                val ext = determineExtension(contentType, imgUrl, bytes)
                val fileName = String.format("%03d.%s", pageNumber, ext)
                val file = File(outputDir, fileName)
                file.writeBytes(bytes)
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries) {
                    delay(1000L * attempt)
                }
            }
        }
        throw IOException("Failed to download page $pageNumber after $maxRetries attempts: ${lastError?.message}", lastError)
    }

    private suspend fun fetchImageBytes(
        source: Source,
        page: Page,
        imageUrl: String?,
    ): Pair<ByteArray, String?> {
        if (source is HttpSource && page.imageUrl != null) {
            try {
                source.getImage(page).use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        return Pair(body.bytes(), response.header("Content-Type"))
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback to direct OkHttp request with source headers
        val url = imageUrl ?: page.url
        val reqBuilder = Request.Builder().url(url)
        if (source is HttpSource) {
            reqBuilder.headers(source.headers)
        }
        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading page image from $url")
            }
            val body = response.body
            return Pair(body.bytes(), response.header("Content-Type"))
        }
    }

    private fun determineExtension(contentType: String?, url: String?, bytes: ByteArray): String {
        // Check magic bytes first
        if (bytes.size >= 4) {
            // PNG: 89 50 4E 47
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                return "png"
            }
            // JPEG: FF D8 FF
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
                return "jpg"
            }
            // GIF: GIF8
            if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) {
                return "gif"
            }
            // WebP: RIFF ... WEBP
            if (bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
            ) {
                return "webp"
            }
        }

        contentType?.lowercase()?.let {
            when {
                it.contains("image/png") -> return "png"
                it.contains("image/jpeg") -> return "jpg"
                it.contains("image/webp") -> return "webp"
                it.contains("image/gif") -> return "gif"
                it.contains("image/avif") -> return "avif"
            }
        }

        url?.let {
            val clean = it.substringBefore("?").substringBefore("#")
            val ext = clean.substringAfterLast(".", "").lowercase()
            if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "avif")) {
                return if (ext == "jpeg") "jpg" else ext
            }
        }

        return "jpg"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
    }
}
