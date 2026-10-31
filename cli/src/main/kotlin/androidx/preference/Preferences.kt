package androidx.preference

import android.content.Context
import android.content.SharedPreferences

open class Preference(val context: Context) {
    var key: String = ""
    var title: CharSequence? = null
    var summary: CharSequence? = null
    private var _defaultValue: Any? = null
    val defaultValue: Any? get() = _defaultValue
    var isEnabled: Boolean = true
    var isVisible: Boolean = true

    var onPreferenceChangeListener: OnPreferenceChangeListener? = null
    var onPreferenceClickListener: OnPreferenceClickListener? = null
    var summaryProvider: SummaryProvider<*>? = null

    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }

    fun interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }

    fun interface SummaryProvider<T : Preference> {
        fun provideSummary(preference: T): CharSequence?
    }

    open fun setDefaultValue(defaultValue: Any?) {
        this._defaultValue = defaultValue
    }

    val sharedPreferences: SharedPreferences?
        get() = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}

open class PreferenceGroup(context: Context) : Preference(context) {
    private val preferences = mutableListOf<Preference>()

    fun addPreference(preference: Preference): Boolean {
        preferences.add(preference)
        return true
    }

    fun getPreferenceCount(): Int = preferences.size
    fun getPreference(index: Int): Preference = preferences[index]
    fun findPreference(key: CharSequence): Preference? = preferences.firstOrNull { it.key == key.toString() }
}

open class PreferenceCategory(context: Context) : PreferenceGroup(context)
open class PreferenceScreen(context: Context) : PreferenceGroup(context)

open class TwoStatePreference(context: Context) : Preference(context) {
    var isChecked: Boolean = false
}

open class SwitchPreferenceCompat(context: Context) : TwoStatePreference(context)
open class CheckBoxPreference(context: Context) : TwoStatePreference(context)

open class DialogPreference(context: Context) : Preference(context) {
    var dialogTitle: CharSequence? = null
    var dialogMessage: CharSequence? = null
}

open class EditTextPreference(context: Context) : DialogPreference(context) {
    var text: String? = null
    var onBindEditTextListener: OnBindEditTextListener? = null

    fun interface OnBindEditTextListener {
        fun onBindEditText(editText: Any)
    }

    class SimpleSummaryProvider private constructor() : SummaryProvider<EditTextPreference> {
        override fun provideSummary(preference: EditTextPreference): CharSequence? = preference.text
        companion object {
            private val instance = SimpleSummaryProvider()
            @JvmStatic fun getInstance(): SimpleSummaryProvider = instance
        }
    }
}

open class ListPreference(context: Context) : DialogPreference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var value: String? = null

    class SimpleSummaryProvider private constructor() : SummaryProvider<ListPreference> {
        override fun provideSummary(preference: ListPreference): CharSequence? = preference.value
        companion object {
            private val instance = SimpleSummaryProvider()
            @JvmStatic fun getInstance(): SimpleSummaryProvider = instance
        }
    }
}

open class DropDownPreference(context: Context) : ListPreference(context)

open class MultiSelectListPreference(context: Context) : DialogPreference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var values: Set<String> = emptySet()

    class SimpleSummaryProvider private constructor() : SummaryProvider<MultiSelectListPreference> {
        override fun provideSummary(preference: MultiSelectListPreference): CharSequence? = preference.values.joinToString(", ")
        companion object {
            private val instance = SimpleSummaryProvider()
            @JvmStatic fun getInstance(): SimpleSummaryProvider = instance
        }
    }
}

open class SeekBarPreference(context: Context) : Preference(context) {
    var value: Int = 0
    var min: Int = 0
    var max: Int = 100
}
