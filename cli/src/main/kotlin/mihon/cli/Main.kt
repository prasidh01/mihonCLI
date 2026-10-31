package mihon.cli

import kotlinx.coroutines.runBlocking
import mihon.cli.commands.ExtensionCommands
import mihon.cli.commands.MangaCommands
import mihon.cli.downloader.MangaDownloader
import mihon.cli.loader.ExtensionLoader
import mihon.cli.store.ExtensionStoreManager
import mihon.cli.store.SourceManager
import mihon.cli.ui.InteractiveNavigator
import mihon.cli.ui.TerminalUi
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val storeManager = ExtensionStoreManager()
    val loader = ExtensionLoader()
    val sourceManager = SourceManager(storeManager, loader)
    val downloader = MangaDownloader()

    val debug = args.contains("--debug")
    val cleanArgs = args.filter { it != "--debug" }

    // If no arguments provided, launch the interactive Android-like main menu
    if (cleanArgs.isEmpty()) {
        try {
            runBlocking {
                InteractiveNavigator.mainMenu(sourceManager, storeManager, loader, downloader)
            }
            exitProcess(0)
        } catch (e: Exception) {
            System.err.println("\nError: ${e.message}")
            if (debug) e.printStackTrace()
            exitProcess(1)
        }
    }

    if (cleanArgs[0] in listOf("-h", "--help", "help")) {
        printUsage()
        return
    }

    try {
        runBlocking {
            when (cleanArgs[0].lowercase()) {
                "extension", "ext" -> handleExtension(cleanArgs.drop(1), storeManager, sourceManager, loader, downloader)
                "sources" -> {
                    if (cleanArgs.contains("--no-interactive")) {
                        ExtensionCommands.list(storeManager, sourceManager, remote = false, query = null, lang = null)
                    } else {
                        InteractiveNavigator.browseInstalledExtensions(sourceManager, downloader, storeManager, loader)
                    }
                }
                "search" -> handleSearch(cleanArgs.drop(1), sourceManager, downloader)
                "info" -> handleInfo(cleanArgs.drop(1), sourceManager)
                "download" -> handleDownload(cleanArgs.drop(1), sourceManager, downloader)
                "read" -> handleRead(cleanArgs.drop(1), sourceManager, downloader)
                else -> {
                    System.err.println("Unknown command: '${cleanArgs[0]}'. Use 'mihon help' for available commands.")
                    exitProcess(1)
                }
            }
        }
        exitProcess(0)
    } catch (e: Exception) {
        System.err.println("\nError: ${e.message}")
        if (debug) {
            e.printStackTrace()
        } else {
            System.err.println("(Run with --debug to view full stacktrace)")
        }
        exitProcess(1)
    }
}

private suspend fun handleExtension(
    args: List<String>,
    storeManager: ExtensionStoreManager,
    sourceManager: SourceManager,
    loader: ExtensionLoader,
    downloader: MangaDownloader,
) {
    if (args.isEmpty()) {
        // 'mihon extension' with no subcommand opens the extension list browser
        InteractiveNavigator.browseInstalledExtensions(sourceManager, downloader, storeManager, loader)
        return
    }

    val subCmd = args[0].lowercase()
    val subArgs = args.drop(1)

    when (subCmd) {
        "list", "ls" -> {
            val remote = subArgs.contains("--remote") || subArgs.contains("-r")
            val nonInteractive = subArgs.contains("--no-interactive")
            val query = getFlagValue(subArgs, "--query", "-q")
            val lang = getFlagValue(subArgs, "--lang", "-l")

            if (remote) {
                if (nonInteractive) {
                    ExtensionCommands.list(storeManager, sourceManager, remote = true, query, lang)
                } else {
                    InteractiveNavigator.browseExtensionStore(storeManager, loader, sourceManager, downloader, initialQuery = query)
                }
            } else {
                if (nonInteractive) {
                    ExtensionCommands.list(storeManager, sourceManager, remote = false, query, lang)
                } else {
                    InteractiveNavigator.browseInstalledExtensions(sourceManager, downloader, storeManager, loader)
                }
            }
        }
        "install", "i", "add" -> {
            // "when the user types extension install it should show all available extensions which can be downloaded"
            val query = if (subArgs.isNotEmpty() && !subArgs[0].startsWith("-")) subArgs[0] else null
            InteractiveNavigator.browseExtensionStore(storeManager, loader, sourceManager, downloader, initialQuery = query)
        }
        "uninstall", "rm", "remove" -> {
            if (subArgs.isEmpty()) {
                val installed = sourceManager.loadAllInstalled()
                if (installed.isEmpty()) {
                    println("No extensions are currently installed.")
                    return
                }
                println("Installed Extensions:")
                for ((idx, ext) in installed.withIndex()) {
                    println("  [${idx + 1}] ${ext.name} (${ext.pkgName})")
                }
                val pick = TerminalUi.promptChoice("Enter extension number to uninstall (1-${installed.size})", default = "")
                val num = pick.toIntOrNull()
                if (num != null && num in 1..installed.size) {
                    ExtensionCommands.uninstall(storeManager, installed[num - 1].pkgName)
                }
            } else {
                ExtensionCommands.uninstall(storeManager, subArgs[0])
            }
        }
        else -> {
            System.err.println("Unknown extension subcommand: '${args[0]}'. Use 'mihon extension list' or 'mihon extension install'.")
            exitProcess(1)
        }
    }
}

private suspend fun handleSearch(
    args: List<String>,
    sourceManager: SourceManager,
    downloader: MangaDownloader,
) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help")) {
        val promptQuery = TerminalUi.prompt(TerminalUi.cyan("Enter search title or keyword: "))
        if (!promptQuery.isNullOrBlank()) {
            InteractiveNavigator.quickSearchAcrossSources(sourceManager, promptQuery, downloader)
        } else {
            println("Usage: mihon search <query> [--source <name>] [--page <n>]")
        }
        return
    }

    val source = getFlagValue(args, "--source", "-s")
    val pageStr = getFlagValue(args, "--page", "-p")
    val page = pageStr?.toIntOrNull() ?: 1
    val nonInteractive = args.contains("--no-interactive")

    // Collect query words (ignoring flags)
    val queryParts = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg in listOf("--source", "-s", "--page", "-p")) {
            i += 2
        } else if (arg == "--no-interactive") {
            i += 1
        } else {
            queryParts.add(arg)
            i++
        }
    }
    val query = queryParts.joinToString(" ")

    if (source.isNullOrBlank() && !nonInteractive) {
        InteractiveNavigator.quickSearchAcrossSources(sourceManager, query, downloader)
    } else {
        MangaCommands.search(sourceManager, query, source, page)
    }
}

private suspend fun handleInfo(args: List<String>, sourceManager: SourceManager) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help")) {
        println("Usage:")
        println("  mihon info <manga-url> [--source <name>]")
        return
    }

    val source = getFlagValue(args, "--source", "-s")
    val mangaUrl = args.first { !it.startsWith("-") && it != source }
    MangaCommands.info(sourceManager, mangaUrl, source)
}

private suspend fun handleDownload(
    args: List<String>,
    sourceManager: SourceManager,
    downloader: MangaDownloader,
) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help")) {
        println("Usage:")
        println("  mihon download <manga-url> --chapter <spec> [--source <name>] [--dest <dir>] [--raw]")
        println("\nChapter spec examples:")
        println("  --chapter 1        (Single chapter 1)")
        println("  --chapter 1-5      (Chapters 1 through 5)")
        println("  --chapter latest   (Most recent chapter)")
        println("  --chapter all      (All available chapters)")
        return
    }

    val chapter = getFlagValue(args, "--chapter", "-c")
        ?: throw IllegalArgumentException("Missing required flag: --chapter <number|range|latest|all>")

    val source = getFlagValue(args, "--source", "-s")
    val destStr = getFlagValue(args, "--dest", "-d")
    val destDir = if (destStr != null) File(destStr) else File(System.getProperty("user.home"), "Manga")
    val asCbz = !args.contains("--raw")

    val mangaUrl = args.firstOrNull { it != chapter && it != source && it != destStr && !it.startsWith("-") }
        ?: throw IllegalArgumentException("Missing manga URL argument")

    MangaCommands.download(sourceManager, downloader, mangaUrl, chapter, source, destDir, asCbz)
}

private suspend fun handleRead(
    args: List<String>,
    sourceManager: SourceManager,
    downloader: MangaDownloader,
) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help")) {
        println("Usage:")
        println("  mihon read <manga-url> --chapter <spec> [--source <name>] [--viewer <cmd>]")
        return
    }

    val chapter = getFlagValue(args, "--chapter", "-c") ?: "latest"
    val source = getFlagValue(args, "--source", "-s")
    val viewer = getFlagValue(args, "--viewer", "-v")
    val destStr = getFlagValue(args, "--dest", "-d")
    val destDir = if (destStr != null) File(destStr) else File(System.getProperty("user.home"), "Manga")

    val mangaUrl = args.firstOrNull { it != chapter && it != source && it != viewer && it != destStr && !it.startsWith("-") }
        ?: throw IllegalArgumentException("Missing manga URL argument")

    MangaCommands.read(sourceManager, downloader, mangaUrl, chapter, source, destDir, viewer)
}

private fun getFlagValue(args: List<String>, vararg flags: String): String? {
    for (i in 0 until args.size - 1) {
        if (args[i] in flags) {
            return args[i + 1]
        }
    }
    return null
}

private fun printUsage() {
    TerminalUi.banner()
    println("USAGE:")
    println("  mihon [command] [options]\n")
    println("INTERACTIVE COMMANDS:")
    println("  mihon                               Launch interactive Android-like Mihon Dashboard")
    println("  mihon extension list                Browse installed extensions & read/download manga")
    println("  mihon extension install [query]     Browse & install extensions from Keiyoushi store")
    println("  mihon search <query>                Search manga across all installed sources\n")
    println("DIRECT CLI COMMANDS:")
    println("  mihon extension list --remote       List available extensions from Keiyoushi store")
    println("  mihon extension uninstall <pkg>     Remove installed extension")
    println("  mihon info <manga-url> [-s source]  Display manga details and chapter list")
    println("  mihon download <url> -c <ch> [-s]   Download chapters to CBZ (or folder)")
    println("  mihon read <url> -c <ch> [-s]       Download and open chapter directly in viewer")
    println("  mihon help                          Show this help manual\n")
    println("EXAMPLES:")
    println("  mihon                               (Interactive main menu)")
    println("  mihon extension list                (Interactive extension & manhwa browser)")
    println("  mihon extension install             (Browse all downloadable extensions)")
    println("  mihon extension install manhwa      (Search and install manhwa extensions)")
    println("  mihon search \"Chainsaw Man\"")
}
