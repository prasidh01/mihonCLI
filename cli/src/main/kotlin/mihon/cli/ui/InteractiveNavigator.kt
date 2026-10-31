package mihon.cli.ui

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import mihon.cli.commands.ExtensionCommands
import mihon.cli.commands.MangaCommands
import mihon.cli.downloader.MangaDownloader
import mihon.cli.loader.ExtensionLoader
import mihon.cli.loader.LoadedExtension
import mihon.cli.store.ExtensionStoreManager
import mihon.cli.store.KeiyoushiRepo
import mihon.cli.store.SourceManager
import mihon.cli.viewer.ViewerLauncher
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InteractiveNavigator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    enum class CatalogMode(val title: String) {
        POPULAR("Popular Manga / Manhwa"),
        LATEST("Latest Updates"),
        SEARCH("Search Results"),
    }

    // =========================================================================
    // 1. TOP-LEVEL MAIN MENU
    // =========================================================================
    suspend fun mainMenu(
        sourceManager: SourceManager,
        storeManager: ExtensionStoreManager,
        loader: ExtensionLoader,
        downloader: MangaDownloader,
    ) {
        while (true) {
            TerminalUi.header("Mihon - Android Manga Experience on Desktop", "Select an option to begin")
            TerminalUi.menuItem("1", "Browse Manga / Manhwa", "Choose from installed extensions")
            TerminalUi.menuItem("2", "Extension Store", "Browse & install extensions from Keiyoushi")
            TerminalUi.menuItem("3", "Quick Search", "Search across all installed sources")
            TerminalUi.menuItem("4", "Manage Extensions", "View installed extensions / uninstall")
            TerminalUi.menuItem("h", "CLI Help", "Show command-line usage manual")
            TerminalUi.menuItem("q", "Quit", "Exit Mihon")

            val choice = TerminalUi.promptChoice("\nSelect an option", default = "1").lowercase()
            when (choice) {
                "1" -> browseInstalledExtensions(sourceManager, downloader, storeManager, loader)
                "2" -> browseExtensionStore(storeManager, loader, sourceManager, downloader)
                "3" -> {
                    val query = TerminalUi.prompt(TerminalUi.cyan("Enter search query (e.g. Solo Leveling): "))
                    if (!query.isNullOrBlank()) {
                        quickSearchAcrossSources(sourceManager, query, downloader)
                    }
                }
                "4" -> manageInstalledExtensions(sourceManager, storeManager)
                "h" -> printHelp()
                "q", "exit" -> return
                else -> TerminalUi.warn("Invalid option '$choice'. Please select 1-4, h, or q.")
            }
        }
    }

    // =========================================================================
    // 2. EXTENSION LIST & BROWSE MANGA FLOW
    // =========================================================================
    suspend fun browseInstalledExtensions(
        sourceManager: SourceManager,
        downloader: MangaDownloader,
        storeManager: ExtensionStoreManager,
        loader: ExtensionLoader,
    ) {
        while (true) {
            val installed = sourceManager.loadAllInstalled()
            if (installed.isEmpty()) {
                TerminalUi.header("Installed Extensions", "No extensions found")
                println("  No extensions are currently installed.")
                val wantInstall = TerminalUi.promptConfirm("Would you like to browse and install extensions from Keiyoushi?", default = true)
                if (wantInstall) {
                    browseExtensionStore(storeManager, loader, sourceManager, downloader)
                    continue
                } else {
                    return
                }
            }

            TerminalUi.header("Installed Extensions (${installed.size})", "Choose an extension to browse its manga / manhwa")
            for ((idx, ext) in installed.withIndex()) {
                val numStr = (idx + 1).toString().padStart(2)
                val srcCount = ext.sources.size
                val srcInfo = if (srcCount == 1) {
                    val s = ext.sources.first()
                    "${s.name} [${s.lang}]"
                } else {
                    "${ext.name} ($srcCount sources)"
                }
                println("  ${TerminalUi.yellow("[$numStr]")} ${TerminalUi.bold(ext.name)} ${TerminalUi.dim("v${ext.versionName}")} - $srcInfo")
            }

            TerminalUi.divider()
            println("  Actions:")
            println("    ${TerminalUi.yellow("[1-${installed.size}]")} Select extension to browse manga")
            println("    ${TerminalUi.yellow("[i]")} Install more extensions from Keiyoushi store")
            println("    ${TerminalUi.yellow("[u]")} Uninstall an extension")
            println("    ${TerminalUi.yellow("[b]")} Back / Quit")

            val choice = TerminalUi.promptChoice("\nEnter choice", default = "1").lowercase()
            when {
                choice == "b" || choice == "q" || choice.isEmpty() -> return
                choice == "i" -> browseExtensionStore(storeManager, loader, sourceManager, downloader)
                choice == "u" -> {
                    val targetNum = TerminalUi.promptInt("Enter extension number to uninstall (1-${installed.size}): ", 1, installed.size)
                    if (targetNum != null) {
                        val toRemove = installed[targetNum - 1]
                        val confirm = TerminalUi.promptConfirm("Are you sure you want to uninstall '${toRemove.name}'?", default = false)
                        if (confirm) {
                            ExtensionCommands.uninstall(storeManager, toRemove.pkgName)
                        }
                    }
                }
                else -> {
                    val num = choice.toIntOrNull()
                    if (num != null && num in 1..installed.size) {
                        val selectedExt = installed[num - 1]
                        selectAndBrowseSource(selectedExt, downloader, sourceManager, storeManager, loader)
                    } else {
                        TerminalUi.warn("Please enter a number between 1 and ${installed.size}, 'i', 'u', or 'b'.")
                    }
                }
            }
        }
    }

    private suspend fun selectAndBrowseSource(
        ext: LoadedExtension,
        downloader: MangaDownloader,
        sourceManager: SourceManager,
        storeManager: ExtensionStoreManager,
        loader: ExtensionLoader,
    ) {
        val catalogueSources = ext.sources.filterIsInstance<CatalogueSource>()
        if (catalogueSources.isEmpty()) {
            TerminalUi.error("Extension '${ext.name}' does not provide any browsable catalogue sources.")
            return
        }

        val chosenSource: CatalogueSource = if (catalogueSources.size == 1) {
            catalogueSources.first()
        } else {
            // Extension provides multiple sources (e.g. MangaDex with 60 languages)
            val defaultEn = catalogueSources.find { it.lang.equals("en", ignoreCase = true) }
            TerminalUi.header("Select Source for '${ext.name}'", "${catalogueSources.size} sources available")
            if (defaultEn != null) {
                println("  ${TerminalUi.yellow("[ 1]")} ${TerminalUi.bold(defaultEn.name)} [en] ${TerminalUi.green("(Default English)")}")
                println("  ${TerminalUi.yellow("[ 2]")} Choose from all ${catalogueSources.size} language sources...")
                val subChoice = TerminalUi.promptChoice("Select source", default = "1")
                if (subChoice == "1") {
                    defaultEn
                } else {
                    pickSourceFromList(catalogueSources) ?: return
                }
            } else {
                pickSourceFromList(catalogueSources) ?: return
            }
        }

        browseSource(chosenSource, downloader)
    }

    private fun pickSourceFromList(sources: List<CatalogueSource>): CatalogueSource? {
        println("\nAvailable sources:")
        for ((idx, s) in sources.withIndex()) {
            val numStr = (idx + 1).toString().padStart(2)
            println("  ${TerminalUi.yellow("[$numStr]")} ${s.name} [${s.lang}]")
        }
        val choice = TerminalUi.promptInt("Select source number (1-${sources.size}): ", 1, sources.size)
        return if (choice != null) sources[choice - 1] else null
    }

    // =========================================================================
    // 3. SOURCE LEVEL: POPULAR / LATEST / SEARCH
    // =========================================================================
    suspend fun browseSource(source: CatalogueSource, downloader: MangaDownloader) {
        while (true) {
            TerminalUi.header("Source: ${source.name} [${source.lang}]", "Choose browsing option")
            TerminalUi.menuItem("1", "Popular Manga / Manhwa", "Browse trending and most-read titles")
            if (source.supportsLatest) {
                TerminalUi.menuItem("2", "Latest Updates", "Browse recently updated chapters")
            } else {
                println("  ${TerminalUi.dim("[2] Latest Updates (Not supported by this source)")}")
            }
            TerminalUi.menuItem("3", "Search Manga / Manhwa", "Search by title or keyword")
            TerminalUi.menuItem("b", "Back to Extensions", "Return to extension list")

            val choice = TerminalUi.promptChoice("\nSelect an option", default = "1").lowercase()
            when (choice) {
                "1" -> browseMangaCatalog(source, downloader, CatalogMode.POPULAR)
                "2" -> {
                    if (source.supportsLatest) {
                        browseMangaCatalog(source, downloader, CatalogMode.LATEST)
                    } else {
                        TerminalUi.warn("Source '${source.name}' does not support latest updates.")
                    }
                }
                "3" -> {
                    val query = TerminalUi.prompt(TerminalUi.cyan("Enter search title or keyword (e.g. Solo Leveling): "))
                    if (!query.isNullOrBlank()) {
                        browseMangaCatalog(source, downloader, CatalogMode.SEARCH, query)
                    }
                }
                "b", "q" -> return
                else -> TerminalUi.warn("Invalid option. Please choose 1, 2, 3, or b.")
            }
        }
    }

    // =========================================================================
    // 4. MANGA CATALOG LIST (PAGINATED & INTERACTIVE)
    // =========================================================================
    private suspend fun browseMangaCatalog(
        source: CatalogueSource,
        downloader: MangaDownloader,
        initialMode: CatalogMode,
        initialQuery: String? = null,
    ) {
        var mode = initialMode
        var query = initialQuery ?: ""
        var page = 1
        var lastPage = -1
        var lastMode: CatalogMode? = null
        var lastQuery: String? = null
        var cachedMangasPage: eu.kanade.tachiyomi.source.model.MangasPage? = null

        while (true) {
            val needsFetch = cachedMangasPage == null || lastPage != page || lastMode != mode || lastQuery != query
            val mangasPage = if (needsFetch) {
                TerminalUi.subHeader("${source.name} > ${mode.title} ${if (query.isNotEmpty()) "(\"$query\")" else ""} (Page $page)")
                print("  Fetching content from ${source.name}... ")
                System.out.flush()

                try {
                    val fetched = when (mode) {
                        CatalogMode.POPULAR -> source.getPopularManga(page)
                        CatalogMode.LATEST -> source.getLatestUpdates(page)
                        CatalogMode.SEARCH -> source.getSearchManga(page, query, FilterList())
                    }
                    println(TerminalUi.green("Done!"))
                    cachedMangasPage = fetched
                    lastPage = page
                    lastMode = mode
                    lastQuery = query
                    fetched
                } catch (e: Exception) {
                    println()
                    TerminalUi.error("Failed to load manga page: ${e.message}")
                    val retry = TerminalUi.promptConfirm("Retry?", default = true)
                    if (retry) continue else return
                }
            } else {
                cachedMangasPage
            }
            val mangas = mangasPage.mangas

            if (mangas.isEmpty()) {
                TerminalUi.warn("No manga found on page $page.")
                TerminalUi.divider()
                println("  Actions: ${TerminalUi.yellow("[p]")} Previous page | ${TerminalUi.yellow("[s]")} New search | ${TerminalUi.yellow("[b]")} Back")
                val act = TerminalUi.promptChoice("Action", default = "b").lowercase()
                when (act) {
                    "p" -> if (page > 1) page--
                    "s" -> {
                        val newQ = TerminalUi.prompt(TerminalUi.cyan("Enter search title or keyword: "))
                        if (!newQ.isNullOrBlank()) {
                            query = newQ
                            mode = CatalogMode.SEARCH
                            page = 1
                        }
                    }
                    else -> return
                }
                continue
            }

            TerminalUi.divider()
            for ((idx, manga) in mangas.withIndex()) {
                val numStr = (idx + 1).toString().padStart(2)
                val cleanTitle = manga.title.ifBlank { "Untitled Manga" }
                println("  ${TerminalUi.yellow("[$numStr]")} ${TerminalUi.bold(cleanTitle)}")
            }

            TerminalUi.divider()
            val navParts = mutableListOf<String>()
            navParts.add("${TerminalUi.yellow("[1-${mangas.size}]")} Select manga")
            if (mangasPage.hasNextPage || mangas.size >= 10) {
                navParts.add("${TerminalUi.yellow("[n]")} Next page")
            }
            if (page > 1) {
                navParts.add("${TerminalUi.yellow("[p]")} Prev page")
            }
            navParts.add("${TerminalUi.yellow("[s]")} Search")
            navParts.add("${TerminalUi.yellow("[b]")} Back")
            println("  Actions: ${navParts.joinToString(" | ")}")

            val input = TerminalUi.promptChoice("\nEnter choice", default = "1").lowercase()
            when (input) {
                "b", "q" -> return
                "n" -> page++
                "p" -> if (page > 1) page-- else TerminalUi.warn("Already on first page.")
                "s" -> {
                    val newQ = TerminalUi.prompt(TerminalUi.cyan("Enter search title or keyword: "))
                    if (!newQ.isNullOrBlank()) {
                        query = newQ
                        mode = CatalogMode.SEARCH
                        page = 1
                    }
                }
                else -> {
                    val num = input.toIntOrNull()
                    if (num != null && num in 1..mangas.size) {
                        val selectedManga = mangas[num - 1]
                        browseMangaDetails(source, selectedManga, downloader)
                    } else {
                        TerminalUi.warn("Please enter a number between 1 and ${mangas.size}, 'n', 'p', 's', or 'b'.")
                    }
                }
            }
        }
    }

    // =========================================================================
    // 5. MANGA DETAILS & CHAPTER SELECTION
    // =========================================================================
    suspend fun browseMangaDetails(
        source: CatalogueSource,
        mangaSummary: SManga,
        downloader: MangaDownloader,
    ) {
        TerminalUi.header("Loading Manga Details", mangaSummary.title)
        print("  Fetching details and chapter list from ${source.name}... ")
        System.out.flush()

        val mangaObj = SManga.create().apply { url = mangaSummary.url }
        val update = try {
            source.getMangaUpdate(mangaObj, emptyList(), fetchDetails = true, fetchChapters = true)
        } catch (e: Exception) {
            println()
            TerminalUi.error("Failed to fetch details: ${e.message}")
            return
        }

        println(TerminalUi.green("Done!"))
        val manga = update.manga
        val allChapters = update.chapters

        while (true) {
            TerminalUi.header(manga.title.ifBlank { mangaSummary.title })
            val author = manga.author?.ifBlank { null } ?: "Unknown"
            val artist = manga.artist?.ifBlank { null } ?: author
            val statusStr = when (manga.status) {
                SManga.ONGOING -> "Ongoing"
                SManga.COMPLETED -> "Completed"
                SManga.LICENSED -> "Licensed"
                SManga.PUBLISHING_FINISHED -> "Finished"
                SManga.CANCELLED -> "Cancelled"
                SManga.ON_HIATUS -> "On Hiatus"
                else -> "Unknown"
            }
            println("  ${TerminalUi.dim("Author:")} $author | ${TerminalUi.dim("Artist:")} $artist | ${TerminalUi.dim("Status:")} ${TerminalUi.green(statusStr)}")
            if (!manga.genre.isNullOrBlank()) {
                println("  ${TerminalUi.dim("Genres:")} ${manga.genre}")
            }
            if (!manga.description.isNullOrBlank()) {
                println("\n  ${TerminalUi.dim("Synopsis:")}")
                println(TerminalUi.wrapText(manga.description.orEmpty(), maxWidth = 76, indent = "    "))
            }

            TerminalUi.divider()
            println("  ${TerminalUi.bold("Total Chapters:")} ${TerminalUi.cyan(allChapters.size.toString())}")

            // Show preview of the top 10 chapters
            val previewCount = minOf(10, allChapters.size)
            if (previewCount > 0) {
                println("  Recent Chapters:")
                for (i in 0 until previewCount) {
                    val ch = allChapters[i]
                    val rowStr = (i + 1).toString().padStart(2)
                    val dateStr = if (ch.date_upload > 0) dateFormat.format(Date(ch.date_upload)) else ""
                    val numInfo = if (ch.chapter_number >= 0) "Ch. ${formatChapterNum(ch.chapter_number)}" else ""
                    val extra = listOf(numInfo, dateStr).filter { it.isNotBlank() }.joinToString(" • ")
                    println("    ${TerminalUi.yellow("[$rowStr]")} ${ch.name} ${TerminalUi.dim("($extra)")}")
                }
                if (allChapters.size > previewCount) {
                    println("    ${TerminalUi.dim("... and ${allChapters.size - previewCount} earlier chapters. Use [l] to list all.")}")
                }
            }

            TerminalUi.divider()
            println("  Actions:")
            println("    ${TerminalUi.yellow("[r]")} Read a chapter (download & open in viewer)")
            println("    ${TerminalUi.yellow("[d]")} Download chapter(s) to CBZ")
            if (allChapters.size > previewCount) {
                println("    ${TerminalUi.yellow("[l]")} List all ${allChapters.size} chapters")
            }
            println("    ${TerminalUi.yellow("[b]")} Back to manga list")

            val choice = TerminalUi.promptChoice("\nChoose action", default = "r").lowercase()
            when (choice) {
                "b", "q" -> return
                "r" -> handleInteractiveRead(source, manga, allChapters, downloader)
                "d" -> handleInteractiveDownload(source, manga, allChapters, downloader)
                "l" -> browseAllChapters(source, manga, allChapters, downloader)
                else -> {
                    // Check if user directly entered a chapter number or row index to read!
                    val resolved = resolveChapter(allChapters, choice)
                    if (resolved != null) {
                        readSpecificChapter(source, manga, resolved, downloader)
                    } else {
                        TerminalUi.warn("Invalid choice '$choice'. Please select r, d, l, or b.")
                    }
                }
            }
        }
    }

    private suspend fun handleInteractiveRead(
        source: CatalogueSource,
        manga: SManga,
        allChapters: List<SChapter>,
        downloader: MangaDownloader,
    ) {
        if (allChapters.isEmpty()) {
            TerminalUi.warn("No chapters available to read.")
            return
        }

        println("\nWhich chapter would you like to read?")
        println("  (Enter chapter number e.g. '105', row index e.g. '#1', or press Enter for latest)")
        val input = TerminalUi.promptChoice("Chapter", default = "latest")
        val target = resolveChapter(allChapters, input)
        if (target == null) {
            TerminalUi.error("Could not find chapter matching '$input'.")
            return
        }

        readSpecificChapter(source, manga, target, downloader)
    }

    private suspend fun readSpecificChapter(
        source: CatalogueSource,
        manga: SManga,
        chapter: SChapter,
        downloader: MangaDownloader,
    ) {
        TerminalUi.status("Preparing '${chapter.name}' for reading...")
        val destDir = File(System.getProperty("user.home"), "Manga")
        try {
            var lastReported = 0
            val file = downloader.downloadChapter(
                source = source,
                manga = manga,
                chapter = chapter,
                destinationDir = destDir,
                asCbz = true,
                onProgress = { current, total ->
                    val pct = (current * 100) / total
                    if (pct >= lastReported + 25 || current == total) {
                        lastReported = pct
                        print("$pct% ")
                        System.out.flush()
                    }
                }
            )
            println(TerminalUi.green(" ✓ Ready!"))
            TerminalUi.status("Launching desktop viewer for ${file.name}...")
            val process = ViewerLauncher.launch(file)
            TerminalUi.success("Opened '${chapter.name}' in viewer.")
        } catch (e: Exception) {
            TerminalUi.error("Failed to download or view chapter: ${e.message}")
        }
    }

    private suspend fun handleInteractiveDownload(
        source: CatalogueSource,
        manga: SManga,
        allChapters: List<SChapter>,
        downloader: MangaDownloader,
    ) {
        if (allChapters.isEmpty()) {
            TerminalUi.warn("No chapters available to download.")
            return
        }

        println("\nEnter chapter specification:")
        println("  • Single chapter: e.g. '105'")
        println("  • Chapter range:  e.g. '1-10'")
        println("  • Most recent:    'latest'")
        println("  • All chapters:   'all'")
        val spec = TerminalUi.promptChoice("Chapter specification", default = "latest")

        val matched = filterChapters(allChapters, spec)
        if (matched.isEmpty()) {
            TerminalUi.error("No chapters matched '$spec'.")
            return
        }

        println("\nFound ${matched.size} chapter(s) to download:")
        for (ch in matched.take(5)) {
            println("  • ${ch.name}")
        }
        if (matched.size > 5) {
            println("  ... and ${matched.size - 5} more.")
        }

        val defaultDest = File(System.getProperty("user.home"), "Manga").absolutePath
        val destInput = TerminalUi.promptChoice("Save destination directory", default = defaultDest)
        val destDir = File(destInput).apply { mkdirs() }

        val formatChoice = TerminalUi.promptChoice("Format: [1] CBZ Comic Archive  [2] Raw images folder", default = "1")
        val asCbz = formatChoice != "2"

        val proceed = TerminalUi.promptConfirm("Start downloading ${matched.size} chapter(s)?", default = true)
        if (!proceed) return

        println()
        for ((idx, ch) in matched.withIndex()) {
            print("  [${idx + 1}/${matched.size}] Downloading '${ch.name}'... ")
            System.out.flush()
            var lastReported = 0
            try {
                val file = downloader.downloadChapter(
                    source = source,
                    manga = manga,
                    chapter = ch,
                    destinationDir = destDir,
                    asCbz = asCbz,
                    onProgress = { current, total ->
                        val pct = (current * 100) / total
                        if (pct >= lastReported + 25 || current == total) {
                            lastReported = pct
                            print("$pct% ")
                            System.out.flush()
                        }
                    }
                )
                println(TerminalUi.green("✓ Done"))
            } catch (e: Exception) {
                println(TerminalUi.red("✗ Failed: ${e.message}"))
            }
        }
        TerminalUi.success("Downloads completed! Saved to ${destDir.absolutePath}")
    }

    private suspend fun browseAllChapters(
        source: CatalogueSource,
        manga: SManga,
        allChapters: List<SChapter>,
        downloader: MangaDownloader,
    ) {
        val pageSize = 25
        var page = 1
        val totalPages = maxOf(1, (allChapters.size + pageSize - 1) / pageSize)

        while (true) {
            val startIndex = (page - 1) * pageSize
            val pageChapters = allChapters.drop(startIndex).take(pageSize)

            TerminalUi.subHeader("Chapters for ${manga.title} (Page $page of $totalPages)")
            for ((idx, ch) in pageChapters.withIndex()) {
                val globalIdx = startIndex + idx + 1
                val numStr = globalIdx.toString().padStart(3)
                val dateStr = if (ch.date_upload > 0) dateFormat.format(Date(ch.date_upload)) else ""
                val chNum = if (ch.chapter_number >= 0) "Ch. ${formatChapterNum(ch.chapter_number)}" else ""
                println("  ${TerminalUi.yellow("[$numStr]")} ${ch.name} ${TerminalUi.dim("($chNum • $dateStr)")}")
            }

            TerminalUi.divider()
            val navParts = mutableListOf<String>()
            navParts.add("${TerminalUi.yellow("[1-${allChapters.size}]")} Read #")
            if (page < totalPages) navParts.add("${TerminalUi.yellow("[n]")} Next")
            if (page > 1) navParts.add("${TerminalUi.yellow("[p]")} Prev")
            navParts.add("${TerminalUi.yellow("[d]")} Download spec")
            navParts.add("${TerminalUi.yellow("[b]")} Back")
            println("  Actions: ${navParts.joinToString(" | ")}")

            val input = TerminalUi.promptChoice("\nEnter choice", default = if (page < totalPages) "n" else "b").lowercase()
            when (input) {
                "b", "q" -> return
                "n" -> if (page < totalPages) page++
                "p" -> if (page > 1) page--
                "d" -> handleInteractiveDownload(source, manga, allChapters, downloader)
                else -> {
                    val resolved = resolveChapter(allChapters, input)
                    if (resolved != null) {
                        readSpecificChapter(source, manga, resolved, downloader)
                    } else {
                        TerminalUi.warn("Please enter a valid chapter number, 'n', 'p', or 'b'.")
                    }
                }
            }
        }
    }

    // =========================================================================
    // 6. EXTENSION STORE (KEIYOUSHI PROTOBUF INDEX)
    // =========================================================================
    suspend fun browseExtensionStore(
        storeManager: ExtensionStoreManager,
        loader: ExtensionLoader,
        sourceManager: SourceManager,
        downloader: MangaDownloader,
        initialQuery: String? = null,
    ) {
        TerminalUi.header("Keiyoushi Extension Store", "Fetching available extensions...")
        val repo = try {
            storeManager.fetchIndex()
        } catch (e: Exception) {
            TerminalUi.error("Failed to connect to Keiyoushi store: ${e.message}")
            return
        }

        val allExtensions = repo.extensionList?.extensions.orEmpty()
        var query = initialQuery ?: ""
        var lang: String? = null
        var page = 1
        val pageSize = 15

        // If an exact package name was passed directly, try to install it immediately
        if (!initialQuery.isNullOrBlank()) {
            val exact = allExtensions.find { it.packageName.equals(initialQuery.trim(), ignoreCase = true) }
            if (exact != null) {
                val confirm = TerminalUi.promptConfirm("Found '${exact.name}' (${exact.packageName}). Install now?", default = true)
                if (confirm) {
                    installAndPromptBrowse(exact, storeManager, loader, sourceManager, downloader)
                    return
                }
            }
        }

        while (true) {
            val installedMap = storeManager.getInstalledMap()

            // Filter extensions
            var filtered = allExtensions
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                filtered = filtered.filter { ext ->
                    ext.name.lowercase().contains(q) ||
                        ext.packageName.lowercase().contains(q) ||
                        ext.sources.any { it.name.lowercase().contains(q) }
                }
            }
            if (!lang.isNullOrBlank()) {
                val l = lang.trim().lowercase()
                filtered = filtered.filter { ext ->
                    ext.sources.any { it.language.lowercase() == l || it.language == "all" }
                }
            }

            val totalPages = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
            page = page.coerceIn(1, totalPages)
            val startIndex = (page - 1) * pageSize
            val pageItems = filtered.drop(startIndex).take(pageSize)

            TerminalUi.header(
                "Keiyoushi Extension Store (${filtered.size} available)",
                "Filter: ${if (query.isNotBlank()) "\"$query\"" else "(none)"} | Lang: ${lang ?: "all"} | Page $page of $totalPages"
            )

            if (pageItems.isEmpty()) {
                println("  No extensions found matching your filter criteria.")
            } else {
                for ((idx, ext) in pageItems.withIndex()) {
                    val numStr = (idx + 1).toString().padStart(2)
                    val isInst = installedMap.containsKey(ext.packageName)
                    val instTag = if (isInst) TerminalUi.green(" [installed]") else ""
                    val langs = ext.sources.map { it.language }.distinct().take(4).joinToString(",")
                    println("  ${TerminalUi.yellow("[$numStr]")} ${TerminalUi.bold(ext.name)}$instTag ${TerminalUi.dim("v${ext.versionName}")} [${langs}]")
                    println("       ${TerminalUi.dim(ext.packageName)}")
                }
            }

            TerminalUi.divider()
            val navParts = mutableListOf<String>()
            if (pageItems.isNotEmpty()) navParts.add("${TerminalUi.yellow("[1-${pageItems.size}]")} Install #")
            navParts.add("${TerminalUi.yellow("[s]")} Search")
            navParts.add("${TerminalUi.yellow("[l]")} Filter lang")
            if (query.isNotBlank() || lang != null) navParts.add("${TerminalUi.yellow("[c]")} Clear filter")
            if (page < totalPages) navParts.add("${TerminalUi.yellow("[n]")} Next")
            if (page > 1) navParts.add("${TerminalUi.yellow("[p]")} Prev")
            navParts.add("${TerminalUi.yellow("[b]")} Back")
            println("  Actions: ${navParts.joinToString(" | ")}")

            val input = TerminalUi.promptChoice("\nEnter action or # to install", default = if (pageItems.isNotEmpty()) "1" else "s").lowercase()
            when (input) {
                "b", "q" -> return
                "n" -> if (page < totalPages) page++
                "p" -> if (page > 1) page--
                "s" -> {
                    val newQ = TerminalUi.prompt(TerminalUi.cyan("Search extensions (e.g. 'asura', 'mangadex', 'manhwa'): "))
                    query = newQ?.trim().orEmpty()
                    page = 1
                }
                "l" -> {
                    val newL = TerminalUi.prompt(TerminalUi.cyan("Filter language (e.g. 'en', 'es', 'ja', 'all'): "))
                    lang = if (newL.isNullOrBlank() || newL.trim().lowercase() == "all") null else newL.trim().lowercase()
                    page = 1
                }
                "c" -> {
                    query = ""
                    lang = null
                    page = 1
                }
                else -> {
                    val num = input.toIntOrNull()
                    if (num != null && num in 1..pageItems.size) {
                        val ext = pageItems[num - 1]
                        installAndPromptBrowse(ext, storeManager, loader, sourceManager, downloader)
                    } else {
                        // User might have typed a search term directly!
                        query = input.trim()
                        page = 1
                    }
                }
            }
        }
    }

    private suspend fun installAndPromptBrowse(
        ext: KeiyoushiRepo.Extension,
        storeManager: ExtensionStoreManager,
        loader: ExtensionLoader,
        sourceManager: SourceManager,
        downloader: MangaDownloader,
    ) {
        TerminalUi.status("Installing '${ext.name}' (${ext.packageName})...")
        try {
            ExtensionCommands.install(storeManager, loader, ext.packageName)
            TerminalUi.success("Successfully installed ${ext.name}!")

            val wantBrowse = TerminalUi.promptConfirm("Would you like to browse manga in '${ext.name}' now?", default = true)
            if (wantBrowse) {
                val loadedExts = sourceManager.loadAllInstalled()
                val loaded = loadedExts.find { it.pkgName == ext.packageName }
                if (loaded != null) {
                    selectAndBrowseSource(loaded, downloader, sourceManager, storeManager, loader)
                }
            }
        } catch (e: Exception) {
            TerminalUi.error("Installation failed: ${e.message}")
        }
    }

    // =========================================================================
    // 7. QUICK SEARCH ACROSS SOURCES
    // =========================================================================
    suspend fun quickSearchAcrossSources(
        sourceManager: SourceManager,
        query: String,
        downloader: MangaDownloader,
    ) {
        val allSources = sourceManager.getAllSources().filterIsInstance<CatalogueSource>()
        if (allSources.isEmpty()) {
            TerminalUi.error("No installed catalogue sources available to search.")
            return
        }

        // Prioritize English sources if present, else distinct sources
        val targetSources = allSources.filter { it.lang.equals("en", ignoreCase = true) }.ifEmpty { allSources.distinctBy { it.name } }
        TerminalUi.header("Searching \"$query\"", "Across ${targetSources.size} sources")

        val results = coroutineScope {
            targetSources.map { src ->
                async {
                    try {
                        val res = src.getSearchManga(1, query, FilterList())
                        Pair(src, res.mangas)
                    } catch (e: Exception) {
                        Pair(src, emptyList<SManga>())
                    }
                }
            }.awaitAll()
        }

        val allFound = mutableListOf<Pair<CatalogueSource, SManga>>()
        for ((src, list) in results) {
            for (m in list) {
                allFound.add(Pair(src, m))
            }
        }

        if (allFound.isEmpty()) {
            TerminalUi.warn("No results found for \"$query\".")
            return
        }

        println("Found ${allFound.size} result(s):\n")
        val format = "  %-4s | %-40s | %-16s"
        println(String.format(format, "#", "TITLE", "SOURCE"))
        TerminalUi.divider()

        val displayList = allFound.take(30)
        for ((idx, item) in displayList.withIndex()) {
            val numStr = (idx + 1).toString().padStart(2)
            val title = if (item.second.title.length > 38) item.second.title.take(35) + "..." else item.second.title
            println(String.format(format, TerminalUi.yellow("[$numStr]"), TerminalUi.bold(title), TerminalUi.dim(item.first.name)))
        }

        if (allFound.size > 30) {
            println("  ... and ${allFound.size - 30} more results.")
        }

        TerminalUi.divider()
        val pick = TerminalUi.promptChoice("Select a manga number to view details & read (1-${displayList.size}), or [Enter] to exit", default = "")
        val num = pick.toIntOrNull()
        if (num != null && num in 1..displayList.size) {
            val chosen = displayList[num - 1]
            browseMangaDetails(chosen.first, chosen.second, downloader)
        }
    }

    // =========================================================================
    // 8. HELPERS & EXTENSION MANAGEMENT
    // =========================================================================
    private fun manageInstalledExtensions(sourceManager: SourceManager, storeManager: ExtensionStoreManager) {
        val installed = sourceManager.loadAllInstalled()
        if (installed.isEmpty()) {
            TerminalUi.warn("No extensions installed.")
            return
        }

        TerminalUi.header("Manage Installed Extensions")
        for ((idx, ext) in installed.withIndex()) {
            val numStr = (idx + 1).toString().padStart(2)
            println("  ${TerminalUi.yellow("[$numStr]")} ${TerminalUi.bold(ext.name)} ${TerminalUi.dim("(${ext.pkgName})")}")
            println("       Sources: ${ext.sources.joinToString(", ") { "${it.name} [${it.lang}]" }}")
        }

        TerminalUi.divider()
        val action = TerminalUi.promptChoice("Enter extension number to uninstall, or [b] to go back", default = "b").lowercase()
        if (action == "b" || action.isEmpty()) return

        val num = action.toIntOrNull()
        if (num != null && num in 1..installed.size) {
            val toRemove = installed[num - 1]
            val confirm = TerminalUi.promptConfirm("Are you sure you want to remove '${toRemove.name}'?", default = false)
            if (confirm) {
                ExtensionCommands.uninstall(storeManager, toRemove.pkgName)
            }
        }
    }

    private fun resolveChapter(chapters: List<SChapter>, spec: String): SChapter? {
        val s = spec.trim().lowercase()
        if (s.isEmpty() || s == "latest") {
            return chapters.firstOrNull()
        }

        // If user explicitly typed row number e.g. "#1" or "#10"
        if (s.startsWith("#")) {
            val row = s.removePrefix("#").toIntOrNull()
            if (row != null && row in 1..chapters.size) {
                return chapters[row - 1]
            }
        }

        // Check exact chapter number float match e.g. "105" or "105.5"
        val fNum = s.toFloatOrNull()
        if (fNum != null) {
            val byNum = chapters.find { it.chapter_number == fNum }
            if (byNum != null) return byNum
        }

        // Check 1-based row index fallback if in range
        val idx = s.toIntOrNull()
        if (idx != null && idx in 1..chapters.size) {
            return chapters[idx - 1]
        }

        // Check substring in chapter name or url
        return chapters.find { it.name.lowercase().contains(s) || it.url.lowercase().contains(s) }
    }

    private fun filterChapters(chapters: List<SChapter>, spec: String): List<SChapter> {
        val s = spec.trim().lowercase()
        if (s == "all") {
            return chapters.reversed() // Oldest to newest
        }
        if (s == "latest" || s.isEmpty()) {
            return listOfNotNull(chapters.firstOrNull())
        }

        // Check range e.g. "1-5"
        if (s.contains("-")) {
            val parts = s.split("-", limit = 2)
            val start = parts[0].trim().toFloatOrNull()
            val end = parts[1].trim().toFloatOrNull()
            if (start != null && end != null) {
                val min = minOf(start, end)
                val max = maxOf(start, end)
                return chapters.filter { it.chapter_number in min..max }.sortedBy { it.chapter_number }
            }
        }

        // Single chapter
        val resolved = resolveChapter(chapters, s)
        return listOfNotNull(resolved)
    }

    private fun formatChapterNum(num: Float): String {
        return if (num % 1f == 0f) num.toInt().toString() else num.toString()
    }

    private fun printHelp() {
        TerminalUi.banner()
        println("USAGE:")
        println("  mihon [command] [options]\n")
        println("COMMANDS:")
        println("  extension list                      Interactive extension & manhwa browser")
        println("  extension install [query]           Interactive Keiyoushi store browser")
        println("  extension uninstall <package>       Uninstall an extension")
        println("  search <query> [-s source]          Search across sources")
        println("  download <url> -c <ch> [-s source]  Download chapter(s) to CBZ")
        println("  read <url> -c <ch> [-s source]      Read chapter directly in desktop viewer")
        println("  help                                Show this help manual\n")
        TerminalUi.prompt("Press Enter to continue...")
    }
}
