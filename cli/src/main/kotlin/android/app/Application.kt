package android.app

import android.content.Context
import android.content.ContextWrapper
import android.content.MockContext

open class Application : ContextWrapper(MockContext()) {
    companion object {
        private var instance: Application? = null
        fun getInstance(): Application {
            return instance ?: Application().also { instance = it }
        }
    }
}
