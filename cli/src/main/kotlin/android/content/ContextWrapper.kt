package android.content

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

open class ContextWrapper(var baseContext: Context? = null) : Context() {

    override val packageName: String
        get() = baseContext?.packageName ?: "eu.kanade.tachiyomi"

    override val applicationInfo: ApplicationInfo
        get() = baseContext?.applicationInfo ?: ApplicationInfo()

    override val packageManager: PackageManager
        get() = baseContext?.packageManager ?: PackageManager()

    override val filesDir: File
        get() = baseContext?.filesDir ?: File(System.getProperty("user.home"), ".local/share/mihon/files")

    override val cacheDir: File
        get() = baseContext?.cacheDir ?: File(System.getProperty("user.home"), ".local/share/mihon/cache")

    override val classLoader: ClassLoader
        get() = baseContext?.classLoader ?: javaClass.classLoader

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return baseContext?.getSharedPreferences(name, mode) ?: MockContext().getSharedPreferences(name, mode)
    }

    override fun getApplicationContext(): Context {
        return baseContext?.getApplicationContext() ?: this
    }

    fun attachBaseContext(base: Context) {
        this.baseContext = base
    }

    override fun getSystemService(name: String): Any? {
        return baseContext?.getSystemService(name)
    }

    override fun getString(resId: Int): String {
        return baseContext?.getString(resId) ?: ""
    }

    override fun getString(resId: Int, vararg formatArgs: Any?): String {
        return baseContext?.getString(resId, *formatArgs) ?: ""
    }
}
