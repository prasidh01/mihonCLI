package mihon.cli.downloader

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CbzPackager {

    /**
     * Packages images in [sourceDir] into a .cbz (ZIP) file at [outputCbzFile].
     * Sorts files using natural numerical sorting so readers view pages in order.
     * Optionally generates ComicInfo.xml metadata.
     */
    fun pack(
        sourceDir: File,
        outputCbzFile: File,
        manga: SManga? = null,
        chapter: SChapter? = null,
    ): File {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            throw IllegalArgumentException("Source directory does not exist: ${sourceDir.absolutePath}")
        }

        outputCbzFile.parentFile?.mkdirs()
        val tempZip = File(outputCbzFile.parentFile, "${outputCbzFile.name}.tmp-${System.currentTimeMillis()}")

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
        val imageFiles = sourceDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in imageExtensions
        }?.toList().orEmpty().sortedWith { a, b -> naturalCompare(a.name, b.name) }

        ZipOutputStream(FileOutputStream(tempZip)).use { zipOut ->
            // Write ComicInfo.xml if metadata is present
            if (manga != null || chapter != null) {
                val comicInfo = buildComicInfoXml(manga, chapter, imageFiles.size)
                val entry = ZipEntry("ComicInfo.xml")
                zipOut.putNextEntry(entry)
                zipOut.write(comicInfo.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            // Write all image files
            for ((index, file) in imageFiles.withIndex()) {
                val ext = file.extension.lowercase()
                val entryName = String.format("%03d.%s", index + 1, ext)
                val entry = ZipEntry(entryName)
                zipOut.putNextEntry(entry)
                file.inputStream().use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }

        if (outputCbzFile.exists()) {
            outputCbzFile.delete()
        }
        if (!tempZip.renameTo(outputCbzFile)) {
            tempZip.copyTo(outputCbzFile, overwrite = true)
            tempZip.delete()
        }

        return outputCbzFile
    }

    private fun buildComicInfoXml(manga: SManga?, chapter: SChapter?, pageCount: Int): String {
        val series = manga?.title?.xmlEscape() ?: ""
        val writer = manga?.author?.xmlEscape() ?: ""
        val artist = manga?.artist?.xmlEscape() ?: ""
        val summary = manga?.description?.xmlEscape() ?: ""
        val genre = manga?.genre?.xmlEscape() ?: ""

        val title = chapter?.name?.xmlEscape() ?: ""
        val number = chapter?.chapter_number?.takeIf { it >= 0 }?.let {
            if (it % 1f == 0f) it.toInt().toString() else it.toString()
        } ?: ""

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<ComicInfo xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">")
            if (title.isNotEmpty()) appendLine("  <Title>$title</Title>")
            if (series.isNotEmpty()) appendLine("  <Series>$series</Series>")
            if (number.isNotEmpty()) appendLine("  <Number>$number</Number>")
            if (writer.isNotEmpty()) appendLine("  <Writer>$writer</Writer>")
            if (artist.isNotEmpty()) appendLine("  <Penciller>$artist</Penciller>")
            if (summary.isNotEmpty()) appendLine("  <Summary>$summary</Summary>")
            if (genre.isNotEmpty()) appendLine("  <Genre>$genre</Genre>")
            appendLine("  <PageCount>$pageCount</PageCount>")
            appendLine("</ComicInfo>")
        }
    }

    private fun String.xmlEscape(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun naturalCompare(a: String, b: String): Int {
        val numRegex = Regex("\\d+")
        val matchA = numRegex.find(a)
        val matchB = numRegex.find(b)
        if (matchA != null && matchB != null && a.substring(0, matchA.range.first) == b.substring(0, matchB.range.first)) {
            val numA = matchA.value.toIntOrNull()
            val numB = matchB.value.toIntOrNull()
            if (numA != null && numB != null && numA != numB) {
                return numA.compareTo(numB)
            }
        }
        return a.compareTo(b, ignoreCase = true)
    }
}
