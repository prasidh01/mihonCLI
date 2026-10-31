package android.content

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap

abstract class Context {
    abstract val packageName: String
    abstract val applicationInfo: ApplicationInfo
    abstract val packageManager: PackageManager
    abstract val filesDir: File
    abstract val cacheDir: File
    abstract val classLoader: ClassLoader

    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    abstract fun getApplicationContext(): Context
    open fun getSystemService(name: String): Any? = null
    open fun getString(resId: Int): String = ""
    open fun getString(resId: Int, vararg formatArgs: Any?): String = ""

    companion object {
        const val MODE_PRIVATE = 0
    }
}

open class MockContext(
    override val packageName: String = "eu.kanade.tachiyomi",
    baseDir: File = File(System.getProperty("user.home"), ".local/share/mihon"),
) : Context() {

    override val applicationInfo = ApplicationInfo().apply {
        packageName = this@MockContext.packageName
        dataDir = baseDir.absolutePath
    }

    override val packageManager = PackageManager()
    override val filesDir = File(baseDir, "files").apply { mkdirs() }
    override val cacheDir = File(baseDir, "cache").apply { mkdirs() }
    override val classLoader: ClassLoader get() = javaClass.classLoader

    private val prefsMap = ConcurrentHashMap<String, SharedPreferences>()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return prefsMap.computeIfAbsent(name) {
            val file = File(File(filesDir.parentFile, "prefs"), "$name.json")
            JsonSharedPreferences(file)
        }
    }

    override fun getApplicationContext(): Context = this
}
