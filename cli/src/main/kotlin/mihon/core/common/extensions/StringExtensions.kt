package mihon.core.common.extensions

import kotlinx.serialization.json.JsonObject

val String.Companion.EMPTY: String
    get() = ""

val JsonObject.Companion.EMPTY: JsonObject
    get() = JsonObject(emptyMap())
