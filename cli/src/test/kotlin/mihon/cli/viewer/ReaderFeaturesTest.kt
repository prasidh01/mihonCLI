package mihon.cli.viewer

import eu.kanade.tachiyomi.source.model.SChapter
import mihon.cli.cleanup.AutoCleanupManager
import mihon.cli.ui.InteractiveNavigator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ReaderFeaturesTest {

    @Test
    fun `findNextChapter finds chronologically next chapter in newest-first list`() {
        val ch3 = SChapter.create().apply {
            url = "/ch3"
            name = "Chapter 3"
            chapter_number = 3f
        }
        val ch2 = SChapter.create().apply {
            url = "/ch2"
            name = "Chapter 2"
            chapter_number = 2f
        }
        val ch1 = SChapter.create().apply {
            url = "/ch1"
            name = "Chapter 1"
            chapter_number = 1f
        }

        val chapters = listOf(ch3, ch2, ch1) // Newest first

        val nextFrom1 = InteractiveNavigator.findNextChapter(chapters, ch1)
        assertNotNull(nextFrom1)
        assertEquals(2f, nextFrom1?.chapter_number)

        val nextFrom2 = InteractiveNavigator.findNextChapter(chapters, ch2)
        assertNotNull(nextFrom2)
        assertEquals(3f, nextFrom2?.chapter_number)

        val nextFrom3 = InteractiveNavigator.findNextChapter(chapters, ch3)
        assertNull(nextFrom3, "No chapter should follow the latest chapter")
    }

    @Test
    fun `resolveChapter matches user input by chapter number rather than row index`() {
        // Chapters arranged newest-first: row 1 is Ch 3, row 3 is Ch 1
        val ch3 = SChapter.create().apply {
            url = "/ch3"
            name = "Chapter 3"
            chapter_number = 3f
        }
        val ch2 = SChapter.create().apply {
            url = "/ch2"
            name = "Chapter 2"
            chapter_number = 2f
        }
        val ch1 = SChapter.create().apply {
            url = "/ch1"
            name = "Chapter 1"
            chapter_number = 1f
        }

        val chapters = listOf(ch3, ch2, ch1)

        // User enters "1" expecting Chapter 1 (not row 1 which is Chapter 3)
        val resolved1 = InteractiveNavigator.resolveChapter(chapters, "1")
        assertNotNull(resolved1)
        assertEquals("Chapter 1", resolved1?.name)
        assertEquals(1f, resolved1?.chapter_number)

        // User enters "3" expecting Chapter 3
        val resolved3 = InteractiveNavigator.resolveChapter(chapters, "3")
        assertNotNull(resolved3)
        assertEquals("Chapter 3", resolved3?.name)
        assertEquals(3f, resolved3?.chapter_number)
    }

    @Test
    fun `LongstripViewer generates zero-gap HTML with natural image order`(@TempDir tempDir: File) {
        val img1 = File(tempDir, "001.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val img2 = File(tempDir, "002.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val img10 = File(tempDir, "010.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }

        val htmlFile = LongstripViewer.createHtml(
            chapterDir = tempDir,
            mangaTitle = "Solo Leveling",
            chapterName = "Chapter 1",
            nextChapterName = "Chapter 2"
        )

        assertTrue(htmlFile.exists())
        val content = htmlFile.readText()

        // Verify zero-gap styling
        assertTrue(content.contains("vertical-align: bottom"))
        assertTrue(content.contains("max-width: 860px"))

        // Verify natural ordering in HTML
        val idx1 = content.indexOf("./001.jpg")
        val idx2 = content.indexOf("./002.jpg")
        val idx10 = content.indexOf("./010.jpg")
        assertTrue(idx1 != -1 && idx2 != -1 && idx10 != -1)
        assertTrue(idx1 < idx2 && idx2 < idx10, "Images must appear in natural ascending order")

        // Verify chapter info & next chapter card
        assertTrue(content.contains("Solo Leveling"))
        assertTrue(content.contains("Chapter 1"))
        assertTrue(content.contains("Chapter 2"))
    }

    @Test
    fun `AutoCleanupManager deletes file after scheduled delay`(@TempDir tempDir: File) {
        val target = File(tempDir, "cached_chapter").apply {
            mkdirs()
            File(this, "page.jpg").writeText("data")
        }
        assertTrue(target.exists())

        AutoCleanupManager.scheduleDeletion(target, delaySeconds = 1)
        Thread.sleep(2500)

        assertFalse(target.exists(), "Target directory must be deleted after scheduled delay")
    }
}
