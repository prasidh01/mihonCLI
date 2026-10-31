package mihon.cli.commands

import mihon.cli.loader.ExtensionLoader
import mihon.cli.store.ExtensionStoreManager
import mihon.cli.store.SourceManager
import java.io.File

object ExtensionCommands {

    fun list(
        storeManager: ExtensionStoreManager,
        sourceManager: SourceManager,
        remote: Boolean,
        query: String?,
        lang: String?,
    ) {
        if (remote) {
            println("Browsing Keiyoushi Extension Repository...")
            val results = storeManager.searchAvailable(query, lang)
            if (results.isEmpty()) {
                println("No extensions found matching criteria.")
                return
            }

            println("Found ${results.size} extensions:\n")
            val format = "%-45s | %-25s | %-10s | %s"
            println(String.format(format, "PACKAGE NAME", "NAME", "VERSION", "LANGUAGES"))
            println("-".repeat(100))

            val installed = storeManager.getInstalledMap()
            for (ext in results.take(50)) {
                val isInst = if (installed.containsKey(ext.packageName)) " [installed]" else ""
                val langs = ext.sources.map { it.language }.distinct().joinToString(",")
                println(String.format(format, ext.packageName, ext.name + isInst, ext.versionName, langs))
            }
            if (results.size > 50) {
                println("\n... and ${results.size - 50} more. Use --query <keyword> to narrow down results.")
            }
        } else {
            println("Installed Extensions:")
            val installed = sourceManager.loadAllInstalled()
            if (installed.isEmpty()) {
                println("No extensions currently installed.")
                println("Use 'mihon extension list --remote' to browse and 'mihon extension install <pkg>' to install.")
                return
            }

            println("-".repeat(80))
            for (ext in installed) {
                println("• ${ext.name} (v${ext.versionName})")
                println("  Package: ${ext.pkgName}")
                println("  Sources (${ext.sources.size}):")
                for (s in ext.sources) {
                    println("    - [${s.id}] ${s.name} (${s.lang})")
                }
            }
            println("-".repeat(80))
        }
    }

    fun install(
        storeManager: ExtensionStoreManager,
        loader: ExtensionLoader,
        pkgName: String,
    ) {
        println("Installing extension '$pkgName'...")
        val apkFile = storeManager.downloadExtension(pkgName)
        try {
            val loaded = loader.loadExtension(apkFile)
            println("\nSuccessfully installed ${loaded.name} (v${loaded.versionName})!")
            println("Provided sources (${loaded.sources.size}):")
            for (s in loaded.sources) {
                println("  - ${s.name} (lang: ${s.lang}, id: ${s.id})")
            }
        } catch (e: Exception) {
            System.err.println("Error validating installed extension: ${e.message}")
            e.printStackTrace()
        }
    }

    fun uninstall(
        storeManager: ExtensionStoreManager,
        pkgName: String,
    ) {
        println("Uninstalling extension '$pkgName'...")
        val success = storeManager.uninstallExtension(pkgName)
        if (success) {
            println("Successfully removed $pkgName.")
        } else {
            println("Extension $pkgName was not found or already removed.")
        }
    }
}
