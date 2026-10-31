package android.os

import java.io.Serializable

object Build {
    @JvmField val MODEL: String = "MihonCLI"
    @JvmField val MANUFACTURER: String = "Mihon"
    @JvmField val BRAND: String = "Mihon"
    @JvmField val DEVICE: String = "desktop"
    @JvmField val PRODUCT: String = "desktop"
    @JvmField val HARDWARE: String = "x86_64"

    object VERSION {
        @JvmField val SDK_INT: Int = 34
        @JvmField val RELEASE: String = "14"
        @JvmField val CODENAME: String = "REL"
        @JvmField val INCREMENTAL: String = "1"
    }

    object VERSION_CODES {
        const val BASE = 1
        const val CUPCAKE = 3
        const val DONUT = 4
        const val ECLAIR = 5
        const val FROYO = 8
        const val GINGERBREAD = 9
        const val HONEYCOMB = 11
        const val ICE_CREAM_SANDWICH = 14
        const val JELLY_BEAN = 16
        const val KITKAT = 19
        const val LOLLIPOP = 21
        const val MARSHMALLOW = 23
        const val NOUGAT = 24
        const val OREO = 26
        const val PIE = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
}

class Bundle : Serializable {
    private val map = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String?) { map[key] = value }
    fun getString(key: String): String? = map[key] as? String
    fun getString(key: String, defaultValue: String): String = (map[key] as? String) ?: defaultValue

    fun putInt(key: String, value: Int) { map[key] = value }
    fun getInt(key: String): Int = (map[key] as? Number)?.toInt() ?: 0
    fun getInt(key: String, defaultValue: Int): Int = (map[key] as? Number)?.toInt() ?: defaultValue

    fun putLong(key: String, value: Long) { map[key] = value }
    fun getLong(key: String): Long = (map[key] as? Number)?.toLong() ?: 0L
    fun getLong(key: String, defaultValue: Long): Long = (map[key] as? Number)?.toLong() ?: defaultValue

    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun getBoolean(key: String): Boolean = (map[key] as? Boolean) ?: false
    fun getBoolean(key: String, defaultValue: Boolean): Boolean = (map[key] as? Boolean) ?: defaultValue

    fun putFloat(key: String, value: Float) { map[key] = value }
    fun getFloat(key: String): Float = (map[key] as? Number)?.toFloat() ?: 0.0f
    fun getFloat(key: String, defaultValue: Float): Float = (map[key] as? Number)?.toFloat() ?: defaultValue

    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun keySet(): Set<String> = map.keys
}

class Handler(val looper: Looper = Looper.getMainLooper()) {
    fun post(r: Runnable): Boolean {
        r.run()
        return true
    }
}

class Looper private constructor() {
    companion object {
        private val mainLooper = Looper()
        @JvmStatic fun getMainLooper(): Looper = mainLooper
        @JvmStatic fun myLooper(): Looper = mainLooper
    }
}
