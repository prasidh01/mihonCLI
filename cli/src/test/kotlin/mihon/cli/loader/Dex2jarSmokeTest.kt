package mihon.cli.loader

import com.googlecode.d2j.dex.Dex2jar
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipFile

class Dex2jarSmokeTest {
    @Test
    fun testDexConversion() {
        val apkFile = File("/tmp/sample_ext.apk")
        if (!apkFile.exists()) return

        val zip = ZipFile(apkFile)
        val dexEntry = zip.getEntry("classes.dex") ?: error("No classes.dex")
        val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
        zip.close()

        val outputJar = File("/tmp/sample_converted.jar")
        println("Converting ${dexBytes.size} bytes of DEX to JAR...")
        Dex2jar.from(dexBytes).to(outputJar.toPath())
        println("Converted JAR created: ${outputJar.exists()}, size: ${outputJar.length()}")
    }
}
