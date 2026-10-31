package mihon.cli

import kotlinx.coroutines.runBlocking
import mihon.cli.commands.ExtensionCommands
import mihon.cli.commands.MangaCommands
import mihon.cli.downloader.MangaDownloader
import mihon.cli.loader.ExtensionLoader
import mihon.cli.store.ExtensionStoreManager
import mihon.cli.store.SourceManager
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help", "help")) {
        printUsage()
        return
    }

    val storeManager = ExtensionStoreManager()
    val loader = ExtensionLoader()
    val sourceManager = SourceManager(storeManager, loader)
    val downloader = MangaDownloader()

    val debug = args.contains("--debug")
    val cleanArgs = args.filter { it != "--debug" }

    try {
        runBlocking {
            when (cleanArgs[0].lowercase()) {
                "extension", "ext" -> handleExtension(cleanArgs.drop(1), storeManager, sourceManager, loader)
                "sources" -> ExtensionCommands.list(storeManager, sourceManager, remote = false, query = null, lang = null)
                "search" -> handleSearch(cleanArgs.drop(1), sourceManager)
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

private fun handleExtension(
    args: List<String>,
    storeManager: ExtensionStoreManager,
    sourceManager: SourceManager,
    loader: ExtensionLoader,
) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help", "help")) {
        println("Usage:")
        println("  mihon extension list [--remote] [--query <term>] [--lang <code>]")
        println("  mihon extension install <package-name>")
        println("  mihon extension uninstall <package-name>")
        return
    }

    when (args[0].lowercase()) {
        "list", "ls" -> {
            val remote = args.contains("--remote") || args.contains("-r")
            val query = getFlagValue(args, "--query", "-q")
            val lang = getFlagValue(args, "--lang", "-l")
            ExtensionCommands.list(storeManager, sourceManager, remote, query, lang)
        }
        "install", "i", "add" -> {
            if (args.size < 2) {
                System.err.println("Error: Package name required. E.g.: mihon extension install eu.kanade.tachiyomi.extension.en.mangadex")
                exitProcess(1)
            }
            ExtensionCommands.install(storeManager, loader, args[1])
        }
        "uninstall", "rm", "remove" -> {
            if (args.size < 2) {
                System.err.println("Error: Package name required. E.g.: mihon extension uninstall eu.kanade.tachiyomi.extension.en.mangadex")
                exitProcess(1)
            }
            ExtensionCommands.uninstall(storeManager, args[1])
        }
        else -> {
            System.err.println("Unknown extension subcommand: '${args[0]}'")
            exitProcess(1)
        }
    }
}

private suspend fun handleSearch(args: List<String>, sourceManager: SourceManager) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help")) {
        println("Usage:")
        println("  mihon search <query> [--source <name>] [--page <n>]")
        return
    }

    val source = getFlagValue(args, "--source", "-s")
    val pageStr = getFlagValue(args, "--page", "-p")
    val page = pageStr?.toIntOrNull() ?: 1

    // Collect query words (ignoring flags)
    val queryParts = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg in listOf("--source", "-s", "--page", "-p")) {
            i += 2
        } else {
            queryParts.add(arg)
            i++
        }
    }
    val query = queryParts.joinToString(" ")

    MangaCommands.search(sourceManager, query, source, page)
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
    val banner = """
   __  ____ __              ________    ____
  /  |/  (_) /_  ____  ____/ / ____/   /  _/
 / /|_/ / / __ \/ __ \/ __  / /       / /  
/ /  / / / / / / /_/ / /_/ / /___   _/ /   
/_/  /_/_/_/ /_/\____/\__,_/\____/  /___/   
Mihon CLI - Native Manga Reader & Keiyoushi Extension Engine
"""
    println(banner)
    println("USAGE:")
    println("  mihon <command> [options]\n")
    println("COMMANDS:")
    println("  extension list [--remote] [-q query] [-l lang]  List installed or remote extensions")
    println("  extension install <package-name>               Download & install extension from Keiyoushi")
    println("  extension uninstall <package-name>             Remove installed extension")
    println("  sources                                        Quick view of installed sources")
    println("  search <query> [-s source] [-p page]           Search manga across installed sources")
    println("  info <manga-url> [-s source]                   Display manga details and chapter list")
    println("  download <url> -c <chapter> [-s src] [--raw]   Download chapters to CBZ (or folder)")
    println("  read <url> -c <chapter> [-s src] [-v viewer]   Download and open chapter in viewer")
    println("  help                                           Show this help manual\n")
    println("EXAMPLES:")
    println("  mihon extension list --remote -q mangadex")
    println("  mihon extension install eu.kanade.tachiyomi.extension.en.mangadex")
    println("  mihon search \"Chainsaw Man\"")
    println("  mihon info \"/title/a7774250-d070-49ee-8de8-f1b7ed717975\" -s MangaDex")
    println("  mihon download \"/title/a7774250-d070-49ee-8de8-f1b7ed717975\" -c 1 -s MangaDex")
    println("  mihon read \"/title/a7774250-d070-49ee-8de8-f1b7ed717975\" -c 1 -s MangaDex")
}
