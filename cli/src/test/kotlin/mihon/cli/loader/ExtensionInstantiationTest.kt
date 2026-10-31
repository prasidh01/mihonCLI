package mihon.cli.loader

import eu.kanade.tachiyomi.source.CatalogueSource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ExtensionInstantiationTest {

    @Test
    fun testLoadAndInstantiateSampleExtension() {
        val apkFile = File("/tmp/sample_ext.apk")
        if (!apkFile.exists()) return

        val loader = ExtensionLoader()
        val loaded = loader.loadExtension(apkFile)

        println("Loaded extension: ${loaded.name} (${loaded.pkgName})")
        println("Sources count: ${loaded.sources.size}")
        assertTrue(loaded.sources.isNotEmpty(), "Should load at least 1 source")
        for (s in loaded.sources) {
            println(" - [${s.id}] ${s.name} (${s.lang})")
            assertTrue(s is CatalogueSource, "Source should implement CatalogueSource")
        }
    }
}
