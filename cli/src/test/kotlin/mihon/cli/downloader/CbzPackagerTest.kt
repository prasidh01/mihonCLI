package mihon.cli.downloader

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

class CbzPackagerTest {

    @Test
    fun testPackDirectoryToCbz(@TempDir tempDir: File) {
        val inputDir = File(tempDir, "raw_pages").apply { mkdirs() }
        File(inputDir, "page_1.png").writeBytes(byteArrayOf(1, 2, 3))
        File(inputDir, "page_2.jpg").writeBytes(byteArrayOf(4, 5, 6))
        File(inputDir, "page_10.png").writeBytes(byteArrayOf(7, 8, 9))

        val manga = SManga.create().apply {
            title = "Test Manga Series"
            author = "Test Author"
            artist = "Test Artist"
            description = "A wonderful test manga."
        }
        val chapter = SChapter.create().apply {
            name = "Chapter 1 - Pilot"
            chapter_number = 1.0f
        }

        val cbzFile = File(tempDir, "chapter1.cbz")
        val result = CbzPackager.pack(inputDir, cbzFile, manga, chapter)

        assertTrue(result.exists())
        assertEquals(cbzFile.absolutePath, result.absolutePath)

        ZipFile(result).use { zip ->
            val entries = zip.entries().toList().map { it.name }
            assertTrue("ComicInfo.xml" in entries)
            assertEquals("001.png", entries[1])
            assertEquals("002.jpg", entries[2])
            assertEquals("003.png", entries[3])

            val comicInfo = zip.getInputStream(zip.getEntry("ComicInfo.xml")).bufferedReader().readText()
            assertTrue(comicInfo.contains("<Title>Chapter 1 - Pilot</Title>"))
            assertTrue(comicInfo.contains("<Series>Test Manga Series</Series>"))
            assertTrue(comicInfo.contains("<Writer>Test Author</Writer>"))
        }
    }
}
