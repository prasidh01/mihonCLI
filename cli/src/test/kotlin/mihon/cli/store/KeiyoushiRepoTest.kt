package mihon.cli.store

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.GZIPInputStream

class KeiyoushiRepoTest {

    @Test
    fun testDecodeKeiyoushiIndex() {
        val testFile = File("/tmp/keiyoushi_index.pb")
        if (!testFile.exists()) return

        val bytes = GZIPInputStream(testFile.inputStream()).use { it.readBytes() }
        val repo = ProtoBuf.decodeFromByteArray<KeiyoushiRepo>(bytes)

        println("Repo Name: ${repo.name}")
        println("Signing Key: ${repo.signingKey}")
        val extensions = repo.extensionList?.extensions.orEmpty()
        println("Extensions count: ${extensions.size}")

        assertTrue(extensions.isNotEmpty(), "Extensions list should not be empty")
        val mangadex = extensions.find { it.name.contains("MangaDex", ignoreCase = true) }
        assertNotNull(mangadex, "MangaDex extension should be present")
        println("MangaDex APK: ${mangadex?.resources?.apkUrl}")
    }
}
