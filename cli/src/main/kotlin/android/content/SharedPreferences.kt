package android.content

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

interface SharedPreferences {
    fun getAll(): Map<String, *>
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }
}

class JsonSharedPreferences(private val file: File) : SharedPreferences {
    private val data = ConcurrentHashMap<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val json = Json { prettyPrint = true }

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val content = file.readText()
            if (content.isNotBlank()) {
                val obj = json.parseToJsonElement(content) as? JsonObject ?: return
                for ((k, elem) in obj) {
                    if (elem is JsonPrimitive) {
                        when {
                            elem.booleanOrNull != null -> data[k] = elem.booleanOrNull
                            elem.longOrNull != null -> data[k] = elem.longOrNull
                            elem.doubleOrNull != null -> data[k] = elem.doubleOrNull
                            else -> data[k] = elem.content
                        }
                    } else {
                        try {
                            data[k] = elem.jsonArray.map { it.jsonPrimitive.content }.toSet()
                        } catch (_: Exception) {
                            data[k] = elem.toString()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to read prefs from ${file.path}: ${e.message}")
        }
    }

    private fun save() = synchronized(this) {
        try {
            file.parentFile?.mkdirs()
            val map = mutableMapOf<String, JsonElement>()
            for ((k, v) in data) {
                when (v) {
                    null -> {}
                    is Boolean -> map[k] = JsonPrimitive(v)
                    is Number -> map[k] = JsonPrimitive(v)
                    is String -> map[k] = JsonPrimitive(v)
                    is Set<*> -> {
                        val strList = v.filterIsInstance<String>()
                        map[k] = kotlinx.serialization.json.JsonArray(strList.map { JsonPrimitive(it) })
                    }
                    else -> map[k] = JsonPrimitive(v.toString())
                }
            }
            file.writeText(json.encodeToString(JsonObject(map)))
        } catch (e: Exception) {
            System.err.println("Failed to write prefs to ${file.path}: ${e.message}")
        }
    }

    override fun getAll(): Map<String, *> = HashMap(data)

    override fun getString(key: String, defValue: String?): String? = (data[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (data[key] as? Set<String>) ?: defValues

    override fun getInt(key: String, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue

    override fun getLong(key: String, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = (data[key] as? Number)?.toFloat() ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply {
            staged[key] = values
            removed.remove(key)
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun remove(key: String): SharedPreferences.Editor = apply {
            removed.add(key)
            staged.remove(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clear = true
            staged.clear()
            removed.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) {
                data.clear()
            }
            for (key in removed) {
                data.remove(key)
            }
            for ((key, value) in staged) {
                if (value == null) data.remove(key) else data[key] = value
            }
            save()
            for (listener in listeners) {
                for (key in staged.keys + removed) {
                    listener.onSharedPreferenceChanged(this@JsonSharedPreferences, key)
                }
            }
        }
    }
}
