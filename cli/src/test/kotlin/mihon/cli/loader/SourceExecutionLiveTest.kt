package mihon.cli.loader

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File

class SourceExecutionLiveTest {

    @Test
    fun testLiveSourceFetch() = runBlocking {
        val apkFile = File("/tmp/sample_ext.apk")
        if (!apkFile.exists()) return@runBlocking

        Injekt.addSingleton(Application.getInstance())
        Injekt.addSingleton(NetworkHelper())

        val loader = ExtensionLoader()
        val loaded = loader.loadExtension(apkFile)
        println("Loaded extension: ${loaded.pkgName} with ${loaded.sources.size} sources")

        val source = loaded.sources.first() as CatalogueSource
        val httpSource = source as? eu.kanade.tachiyomi.source.online.HttpSource
        println("Source: ${source.name} (baseUrl: ${httpSource?.baseUrl})")

        try {
            val page = source.getPopularManga(1)
            println("Popular manga found: ${page.mangas.size}")
            for (manga in page.mangas.take(5)) {
                println(" - [${manga.title}] -> url=${manga.url}, thumb=${manga.thumbnail_url}")
            }
            assertTrue(page.mangas.isNotEmpty(), "Should fetch popular mangas")
        } catch (e: Exception) {
            println("Live fetch warning (site might be down/geo-blocked): ${e.message}")
        }
    }
}
