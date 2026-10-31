package mihon.cli.commands

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mihon.cli.cleanup.AutoCleanupManager
import mihon.cli.downloader.MangaDownloader
import mihon.cli.store.SourceManager
import mihon.cli.ui.InteractiveNavigator
import mihon.cli.ui.TerminalUi
import mihon.cli.viewer.LongstripViewer
import mihon.cli.viewer.ReadingMode
import mihon.cli.viewer.ViewerLauncher
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MangaCommands {

    suspend fun search(
        sourceManager: SourceManager,
        query: String,
        sourceName: String?,
        page: Int = 1,
    ) {
        val sourcesToQuery: List<CatalogueSource> = if (!sourceName.isNullOrBlank()) {
            val s = sourceManager.findSource(sourceName)
            listOf(s as? CatalogueSource ?: throw IllegalArgumentException("Source '${s.name}' does not support catalogue search."))
        } else {
            val all = sourceManager.getAllSources().filterIsInstance<CatalogueSource>()
            if (all.isEmpty()) {
                throw IllegalStateException("No catalogue sources found in installed extensions.")
            }
            val enSources = all.filter { it.lang.equals("en", ignoreCase = true) }
            if (enSources.isNotEmpty()) enSources else all.distinctBy { it.name }
        }

        println("Searching for \"$query\" across ${sourcesToQuery.size} source(s)... (Page $page)\n")

        val results = coroutineScope {
            sourcesToQuery.map { src ->
                async {
                    try {
                        val mangaPage = if (query.isBlank()) {
                            src.getPopularManga(page)
                        } else {
                            src.getSearchManga(page, query, FilterList())
                        }
                        Pair(src, mangaPage.mangas)
                    } catch (e: Exception) {
                        System.err.println("Warning: failed search on ${src.name}: ${e.message}")
                        e.printStackTrace()
                        Pair(src, emptyList<SManga>())
                    }
                }
            }.awaitAll()
        }

        var totalFound = 0
        val format = "%-4s | %-40s | %-15s | %s"
        println(String.format(format, "#", "TITLE", "SOURCE", "URL"))
        println("-".repeat(110))

        for ((src, mangas) in results) {
            for (manga in mangas) {
                totalFound++
                val shortTitle = if (manga.title.length > 38) manga.title.take(35) + "..." else manga.title
                println(String.format(format, totalFound, shortTitle, src.name, manga.url))
            }
        }

        println("-".repeat(110))
        println("Found $totalFound result(s).")
        if (totalFound > 0) {
            println("\nTo view details and chapters, run:")
            println("  mihon info <manga-url> [--source <source-name>]")
        }
    }

    suspend fun info(
        sourceManager: SourceManager,
        mangaUrl: String,
        sourceName: String?,
    ) {
        val source = sourceManager.findSource(sourceName)
        println("Fetching details from ${source.name}...")

        val mangaObj = SManga.create().apply { url = mangaUrl }
        val update = source.getMangaUpdate(mangaObj, emptyList(), fetchDetails = true, fetchChapters = true)
        val manga = update.manga
        val chapters = update.chapters

        val statusStr = when (manga.status) {
            SManga.ONGOING -> "Ongoing"
            SManga.COMPLETED -> "Completed"
            SManga.LICENSED -> "Licensed"
            SManga.PUBLISHING_FINISHED -> "Publishing Finished"
            SManga.CANCELLED -> "Cancelled"
            SManga.ON_HIATUS -> "On Hiatus"
            else -> "Unknown"
        }

        println("\n" + "=".repeat(80))
        println("Title:       ${manga.title.ifBlank { "N/A" }}")
        println("Author:      ${manga.author ?: "Unknown"}")
        println("Artist:      ${manga.artist ?: "Unknown"}")
        println("Status:      $statusStr")
        println("Genres:      ${manga.genre ?: "N/A"}")
        println("Source:      ${source.name} (${source.lang})")
        println("URL:         ${manga.url}")
        println("-".repeat(80))
        if (!manga.description.isNullOrBlank()) {
            println("Description:")
            println(wrapText(manga.description!!.trim(), 80))
            println("-".repeat(80))
        }

        println("Chapters (${chapters.size}):")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val format = "  %-5s | %-45s | %-12s | %s"
        println(String.format(format, "CH #", "NAME", "DATE", "URL"))
        println("  " + "-".repeat(95))

        val displayChapters = chapters.take(30)
        for (ch in displayChapters) {
            val numStr = if (ch.chapter_number >= 0) {
                if (ch.chapter_number % 1f == 0f) ch.chapter_number.toInt().toString() else ch.chapter_number.toString()
            } else "-"
            val nameStr = if (ch.name.length > 43) ch.name.take(40) + "..." else ch.name
            val dateStr = if (ch.date_upload > 0) dateFormat.format(Date(ch.date_upload)) else "-"
            println(String.format(format, numStr, nameStr, dateStr, ch.url))
        }

        if (chapters.size > 30) {
            println("  ... and ${chapters.size - 30} earlier chapters.")
        }
        println("=".repeat(80))
        println("\nTo download chapter(s):")
        println("  mihon download \"$mangaUrl\" --chapter <num|all> [--source \"${source.name}\"]")
        println("To read directly:")
        println("  mihon read \"$mangaUrl\" --chapter <num> [--source \"${source.name}\"]")
    }

    suspend fun download(
        sourceManager: SourceManager,
        downloader: MangaDownloader,
        mangaUrl: String,
        chapterSpec: String,
        sourceName: String?,
        destDir: File,
        asCbz: Boolean = true,
    ): List<File> {
        val source = sourceManager.findSource(sourceName)
        val mangaObj = SManga.create().apply { url = mangaUrl }
        println("Fetching chapter list from ${source.name}...")
        val update = source.getMangaUpdate(mangaObj, emptyList(), fetchDetails = true, fetchChapters = true)
        val manga = update.manga
        val allChapters = update.chapters

        if (allChapters.isEmpty()) {
            throw IllegalStateException("No chapters found for this manga.")
        }

        val matched = filterChapters(allChapters, chapterSpec)
        if (matched.isEmpty()) {
            throw IllegalArgumentException("No chapters matched '$chapterSpec'. Available chapter numbers: " +
                allChapters.take(10).joinToString(", ") { it.chapter_number.toString() } + (if (allChapters.size > 10) "..." else ""))
        }

        println("Found ${matched.size} chapter(s) to download:")
        for (ch in matched) {
            println("  • Chapter ${ch.chapter_number}: ${ch.name}")
        }
        println()

        val downloadedFiles = mutableListOf<File>()
        for ((idx, ch) in matched.withIndex()) {
            print("[${idx + 1}/${matched.size}] Downloading '${ch.name}'... ")
            System.out.flush()

            var lastReported = 0
            val file = downloader.downloadChapter(
                source = source,
                manga = manga,
                chapter = ch,
                destinationDir = destDir,
                asCbz = asCbz,
                onProgress = { current, total ->
                    val pct = (current * 100) / total
                    if (pct >= lastReported + 20 || current == total) {
                        lastReported = pct
                        print("$pct% ")
                        System.out.flush()
                    }
                }
            )
            println("✓ Done -> ${file.absolutePath}")
            downloadedFiles.add(file)
        }

        println("\nAll downloads finished successfully! Files saved in: ${destDir.absolutePath}")
        return downloadedFiles
    }

    suspend fun read(
        sourceManager: SourceManager,
        downloader: MangaDownloader,
        mangaUrl: String,
        chapterSpec: String,
        sourceName: String?,
        destDir: File? = null,
        customViewer: String? = null,
        mode: ReadingMode = ReadingMode.LONGSTRIP,
        autoAdvance: Boolean = true,
    ) {
        val source = sourceManager.findSource(sourceName)
        val mangaObj = SManga.create().apply { url = mangaUrl }
        println("Fetching chapter list from ${source.name}...")
        val update = source.getMangaUpdate(mangaObj, emptyList(), fetchDetails = true, fetchChapters = true)
        val manga = update.manga
        val allChapters = update.chapters

        if (allChapters.isEmpty()) {
            throw IllegalStateException("No chapters found for this manga.")
        }

        val initialChapter = filterChapters(allChapters, chapterSpec).firstOrNull()
            ?: throw IllegalArgumentException("No chapters matched '$chapterSpec'.")

        val readerCacheBase = destDir ?: File(System.getProperty("user.home"), ".cache/mihon/reader").apply { mkdirs() }
        AutoCleanupManager.cleanOrphanedCache(readerCacheBase)

        var currentChapter: SChapter? = initialChapter

        while (currentChapter != null) {
            val nextChapter = InteractiveNavigator.findNextChapter(allChapters, currentChapter)

            println("\n[Reading] ${manga.title} - ${currentChapter.name}")
            println("Viewing Mode: ${mode.label}")
            print("Downloading pages... ")
            System.out.flush()

            var lastReported = 0
            val downloadedTarget = downloader.downloadChapter(
                source = source,
                manga = manga,
                chapter = currentChapter,
                destinationDir = readerCacheBase,
                asCbz = (mode == ReadingMode.PAGINATED),
                onProgress = { current, total ->
                    val pct = (current * 100) / total
                    if (pct >= lastReported + 25 || current == total) {
                        lastReported = pct
                        print("$pct% ")
                        System.out.flush()
                    }
                }
            )
            println("✓ Done")

            val (launchTarget, deleteTarget) = if (mode == ReadingMode.LONGSTRIP) {
                val htmlFile = LongstripViewer.createHtml(
                    chapterDir = downloadedTarget,
                    mangaTitle = manga.title,
                    chapterName = currentChapter.name,
                    nextChapterName = nextChapter?.name
                )
                Pair(htmlFile, downloadedTarget)
            } else {
                Pair(downloadedTarget, downloadedTarget)
            }

            println("Launching viewer for '${currentChapter.name}'...")
            val process = ViewerLauncher.launch(launchTarget, customViewer)
            process.waitFor()

            // Auto-cleanup after 5 minutes
            AutoCleanupManager.scheduleDeletion(deleteTarget, delaySeconds = 300)
            println("✓ Finished reading '${currentChapter.name}'! (Chapter cache will be deleted in 5 minutes)")

            if (autoAdvance && nextChapter != null) {
                println("\nNext chapter available: ${nextChapter.name}")
                print("Auto-opening next chapter in 2 seconds... (Press 'q' + Enter to stop): ")
                System.out.flush()
                val userResponse = TerminalUi.promptWithTimeout(2000, default = "continue")
                if (userResponse.lowercase() in listOf("q", "quit", "stop", "exit")) {
                    break
                }
                currentChapter = nextChapter
            } else {
                if (nextChapter == null) {
                    println("🎉 You have reached the latest available chapter!")
                }
                break
            }
        }
    }

    private fun filterChapters(chapters: List<SChapter>, spec: String): List<SChapter> {
        val s = spec.trim().lowercase()
        if (s == "all") {
            return chapters.reversed() // Download oldest to newest
        }
        if (s == "latest") {
            return listOf(chapters.first())
        }

        // Check range e.g. "1-5" or "10-20"
        if (s.contains("-")) {
            val parts = s.split("-", limit = 2)
            val start = parts[0].trim().toFloatOrNull()
            val end = parts[1].trim().toFloatOrNull()
            if (start != null && end != null) {
                val min = minOf(start, end)
                val max = maxOf(start, end)
                return chapters.filter { it.chapter_number in min..max }
                    .sortedBy { it.chapter_number }
            }
        }

        // Single number e.g. "5" or "10.5"
        val singleNum = s.toFloatOrNull()
        if (singleNum != null) {
            val byNum = chapters.filter { it.chapter_number == singleNum }
            if (byNum.isNotEmpty()) return byNum
            val regexMatch = chapters.filter { ch ->
                val match = Regex("(?:ch\\.?|chapter|#)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).find(ch.name)
                match?.groupValues?.get(1)?.toFloatOrNull() == singleNum
            }
            if (regexMatch.isNotEmpty()) return regexMatch
        }

        // Substring in chapter name or URL
        return chapters.filter { it.name.lowercase().contains(s) || it.url.lowercase().contains(s) }
    }

    private fun wrapText(text: String, maxWidth: Int): String {
        val words = text.split(Regex("\\s+"))
        val sb = StringBuilder()
        var currentLineLength = 0
        for (w in words) {
            if (currentLineLength + w.length + 1 > maxWidth) {
                sb.append("\n")
                currentLineLength = 0
            } else if (currentLineLength > 0) {
                sb.append(" ")
                currentLineLength += 1
            }
            sb.append(w)
            currentLineLength += w.length
        }
        return sb.toString()
    }
}
