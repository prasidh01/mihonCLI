package mihon.cli.store

import eu.kanade.tachiyomi.source.Source
import mihon.cli.loader.ExtensionLoader
import mihon.cli.loader.LoadedExtension

class SourceManager(
    val storeManager: ExtensionStoreManager = ExtensionStoreManager(),
    val loader: ExtensionLoader = ExtensionLoader(),
) {
    private val loadedMap = mutableMapOf<String, LoadedExtension>()

    fun loadAllInstalled(): List<LoadedExtension> {
        val apks = storeManager.getInstalledApks()
        val results = mutableListOf<LoadedExtension>()
        for (apk in apks) {
            val cached = loadedMap[apk.absolutePath]
            if (cached != null) {
                results.add(cached)
            } else {
                try {
                    val loaded = loader.loadExtension(apk)
                    loadedMap[apk.absolutePath] = loaded
                    results.add(loaded)
                } catch (e: Exception) {
                    System.err.println("Warning: failed to load extension ${apk.name}: ${e.message}")
                }
            }
        }
        return results
    }

    fun getAllSources(): List<Source> {
        return loadAllInstalled().flatMap { it.sources }
    }

    fun findSource(identifier: String?): Source {
        val allSources = getAllSources()
        if (allSources.isEmpty()) {
            throw IllegalStateException(
                "No manga extensions are installed yet.\n" +
                "Run 'mihon extension list --remote' to browse available extensions, or\n" +
                "'mihon extension install <package-name>' (e.g. 'mihon extension install eu.kanade.tachiyomi.extension.all.mangadex')"
            )
        }

        if (identifier.isNullOrBlank()) {
            if (allSources.size == 1) {
                return allSources.first()
            }
            // Check if there is an English source
            val enSource = allSources.find { it.lang == "en" }
            if (enSource != null) {
                return enSource
            }
            val names = allSources.take(10).joinToString(", ") { "${it.name} (${it.lang})" }
            throw IllegalArgumentException(
                "Multiple sources are installed ($names...).\n" +
                "Please specify one using '--source <name>' or '-s <name>'."
            )
        }

        // Match by exact numeric ID
        val idLong = identifier.toLongOrNull()
        if (idLong != null) {
            val byId = allSources.find { it.id == idLong }
            if (byId != null) return byId
        }

        val raw = identifier.trim()
        val q = raw.lowercase()

        // Check format "Name (lang)" or "Name [lang]" or "Name:lang"
        val langPattern = Regex("^(.*?)[\\s:(/\\[]+([a-zA-Z\\-]+)[)\\]]?$")
        val match = langPattern.matchEntire(raw)
        if (match != null) {
            val namePart = match.groupValues[1].trim().lowercase()
            val langPart = match.groupValues[2].trim().lowercase()
            val candidate = allSources.find {
                it.name.lowercase() == namePart && it.lang.lowercase() == langPart
            }
            if (candidate != null) return candidate
        }

        // Exact name match
        val exactMatches = allSources.filter { it.name.lowercase() == q }
        if (exactMatches.size == 1) {
            return exactMatches.first()
        }
        if (exactMatches.size > 1) {
            // Prefer English if present
            val en = exactMatches.find { it.lang.equals("en", ignoreCase = true) }
            if (en != null) return en
            return exactMatches.first()
        }

        // Substring match
        val subMatches = allSources.filter {
            it.name.lowercase().contains(q) || "${it.name} (${it.lang})".lowercase().contains(q)
        }
        if (subMatches.size == 1) {
            return subMatches.first()
        }
        if (subMatches.size > 1) {
            val en = subMatches.find { it.lang.equals("en", ignoreCase = true) }
            if (en != null) return en
            return subMatches.first()
        }

        val available = allSources.take(15).joinToString(", ") { "${it.name} (${it.lang})" }
        throw IllegalArgumentException("Source '$identifier' not found among installed extensions ($available...).")
    }
}
