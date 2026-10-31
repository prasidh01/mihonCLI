package mihon.cli.viewer

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ViewerLauncher {

    private val KNOWN_CBZ_VIEWERS = listOf("zathura", "mcomix", "yacreader", "evince")
    private val KNOWN_IMAGE_VIEWERS = listOf("imv", "feh", "qview", "sxiv", "nsxiv", "eog")
    private val KNOWN_APP_BROWSERS = listOf("chromium", "google-chrome", "brave", "microsoft-edge")

    fun launch(target: File, customViewer: String? = null): Process {
        if (!target.exists()) {
            throw IllegalArgumentException("Target does not exist: ${target.absolutePath}")
        }

        val command = buildCommand(target, customViewer)
        println("Opening manga with: ${command.joinToString(" ")}")

        val processBuilder = ProcessBuilder(command)
        // Discard browser diagnostic stderr/stdout to avoid cluttering terminal UI
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)
        return processBuilder.start()
    }

    private fun buildCommand(target: File, customViewer: String?): List<String> {
        val isHtml = target.isFile && (target.name.endsWith(".html", ignoreCase = true) || target.name.endsWith(".htm", ignoreCase = true))
        val isCbz = target.isFile && target.name.endsWith(".cbz", ignoreCase = true)

        if (!customViewer.isNullOrBlank()) {
            val viewerBin = customViewer.trim()
            if (isCbz && isImageViewerOnly(viewerBin)) {
                // Extract CBZ to temp dir for image viewer
                val extractedDir = extractCbzToTemp(target)
                return listOf(viewerBin, extractedDir.absolutePath)
            }
            return listOf(viewerBin, target.absolutePath)
        }

        // 1. HTML Longstrip mode
        if (isHtml) {
            val profileDir = File(System.getProperty("user.home"), ".cache/mihon/reader-profile").apply { mkdirs() }
            for (b in KNOWN_APP_BROWSERS) {
                if (isExecutableAvailable(b)) {
                    return listOf(
                        b,
                        "--user-data-dir=${profileDir.absolutePath}",
                        "--app=file://${target.absolutePath}",
                        "--log-level=3",
                        "--disable-sync",
                    )
                }
            }
            if (isExecutableAvailable("firefox")) {
                return listOf("firefox", "--new-window", target.absolutePath)
            }
            if (isExecutableAvailable("xdg-open")) {
                return listOf("xdg-open", target.absolutePath)
            }
            throw IllegalStateException("No suitable browser found to open Longstrip HTML reader. Please install Chromium, Firefox, or specify --viewer.")
        }

        // 2. CBZ file
        if (isCbz) {
            for (v in KNOWN_CBZ_VIEWERS) {
                if (isExecutableAvailable(v)) {
                    return listOf(v, target.absolutePath)
                }
            }
            for (v in KNOWN_IMAGE_VIEWERS) {
                if (isExecutableAvailable(v)) {
                    val extractedDir = extractCbzToTemp(target)
                    return listOf(v, extractedDir.absolutePath)
                }
            }
            if (isExecutableAvailable("xdg-open")) {
                return listOf("xdg-open", target.absolutePath)
            }
        } else {
            // 3. Directory of images
            for (v in KNOWN_IMAGE_VIEWERS) {
                if (isExecutableAvailable(v)) {
                    return listOf(v, target.absolutePath)
                }
            }
            if (isExecutableAvailable("xdg-open")) {
                return listOf("xdg-open", target.absolutePath)
            }
        }

        throw IllegalStateException("No suitable image or comic viewer found. Please install 'imv', 'zathura', or specify with --viewer <command>.")
    }

    private fun isImageViewerOnly(command: String): Boolean {
        val base = File(command).name.lowercase()
        return base in KNOWN_IMAGE_VIEWERS
    }

    private fun isExecutableAvailable(cmd: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparator).any { dir ->
            val exe = File(dir, cmd)
            exe.exists() && exe.canExecute()
        }
    }

    private fun extractCbzToTemp(cbzFile: File): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "mihon-view-${cbzFile.nameWithoutExtension}-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        ZipFile(cbzFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory || entry.name.equals("ComicInfo.xml", ignoreCase = true)) continue
                val outFile = File(tempDir, entry.name)
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return tempDir
    }
}
