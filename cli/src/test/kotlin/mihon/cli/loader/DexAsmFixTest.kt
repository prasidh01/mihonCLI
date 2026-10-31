package mihon.cli.loader

import com.googlecode.d2j.dex.Dex2jar
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

class DexAsmFixTest {

    @Test
    fun testConvertAndFixFrames() {
        val apkFile = File("/tmp/sample_ext.apk")
        if (!apkFile.exists()) return

        val zip = ZipFile(apkFile)
        val dexEntry = zip.getEntry("classes.dex") ?: error("No classes.dex")
        val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
        zip.close()

        val rawJar = File("/tmp/sample_raw.jar")
        Dex2jar.from(dexBytes).to(rawJar.toPath())

        val fixedJar = File("/tmp/sample_fixed.jar")
        val jarFile = JarFile(rawJar)
        JarOutputStream(FileOutputStream(fixedJar)).use { jos ->
            val entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryBytes = jarFile.getInputStream(entry).use { it.readBytes() }
                jos.putNextEntry(JarEntry(entry.name))
                if (entry.name.endsWith(".class")) {
                    try {
                        val cr = ClassReader(entryBytes)
                        val cw = object : ClassWriter(cr, COMPUTE_MAXS) {}
                        cr.accept(cw, 0)
                        jos.write(cw.toByteArray())
                    } catch (e: Exception) {
                        println("Warning: could not compute frames for ${entry.name}: ${e.message}")
                        jos.write(entryBytes)
                    }
                } else {
                    jos.write(entryBytes)
                }
                jos.closeEntry()
            }
        }
        jarFile.close()
        println("Fixed jar created: ${fixedJar.length()} bytes")
    }
}
