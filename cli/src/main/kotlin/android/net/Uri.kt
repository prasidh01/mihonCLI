package android.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.Serializable
import java.net.URI

class Uri private constructor(
    private val rawUri: String,
    private val httpUrl: HttpUrl?,
) : Comparable<Uri>, Serializable {

    val path: String?
        get() = httpUrl?.encodedPath ?: try { URI(rawUri).path } catch (_: Exception) { null }

    val host: String?
        get() = httpUrl?.host ?: try { URI(rawUri).host } catch (_: Exception) { null }

    val scheme: String?
        get() = httpUrl?.scheme ?: try { URI(rawUri).scheme } catch (_: Exception) { null }

    val query: String?
        get() = httpUrl?.encodedQuery ?: try { URI(rawUri).query } catch (_: Exception) { null }

    val fragment: String?
        get() = httpUrl?.fragment ?: try { URI(rawUri).fragment } catch (_: Exception) { null }

    fun getQueryParameter(key: String): String? {
        return httpUrl?.queryParameter(key)
    }

    fun getQueryParameters(key: String): List<String> {
        return httpUrl?.queryParameterValues(key)?.filterNotNull() ?: emptyList()
    }

    val queryParameterNames: Set<String>
        get() = httpUrl?.queryParameterNames ?: emptySet()

    fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        val value = getQueryParameter(key) ?: return defaultValue
        return value.equals("true", ignoreCase = true) || value == "1"
    }

    fun buildUpon(): Builder {
        return Builder(rawUri)
    }

    override fun toString(): String = rawUri

    override fun compareTo(other: Uri): Int = rawUri.compareTo(other.rawUri)

    override fun equals(other: Any?): Boolean {
        return other is Uri && rawUri == other.rawUri
    }

    override fun hashCode(): Int = rawUri.hashCode()

    class Builder(base: String? = null) {
        private var urlBuilder: HttpUrl.Builder? = base?.toHttpUrlOrNull()?.newBuilder()
        private var fallbackUrl: String = base ?: ""

        fun scheme(scheme: String) = apply {
            urlBuilder?.scheme(scheme)
        }

        fun authority(authority: String) = apply {
            urlBuilder?.host(authority)
        }

        fun path(path: String) = apply {
            if (urlBuilder != null) {
                val clean = path.trimStart('/')
                urlBuilder?.encodedPath("/$clean")
            } else {
                fallbackUrl = path
            }
        }

        fun appendPath(newSegment: String) = apply {
            urlBuilder?.addPathSegment(newSegment) ?: run {
                fallbackUrl = if (fallbackUrl.endsWith("/")) fallbackUrl + newSegment else "$fallbackUrl/$newSegment"
            }
        }

        fun appendQueryParameter(key: String, value: String?) = apply {
            urlBuilder?.addQueryParameter(key, value.orEmpty()) ?: run {
                val sep = if (fallbackUrl.contains("?")) "&" else "?"
                fallbackUrl += "$sep$key=${value.orEmpty()}"
            }
        }

        fun clearQuery() = apply {
            urlBuilder?.query(null)
        }

        fun build(): Uri {
            val finalStr = urlBuilder?.build()?.toString() ?: fallbackUrl
            return parse(finalStr)
        }
    }

    companion object {
        val EMPTY = Uri("", null)

        @JvmStatic
        fun parse(uriString: String): Uri {
            val httpUrl = uriString.toHttpUrlOrNull()
            return Uri(uriString, httpUrl)
        }

        @JvmStatic
        fun fromFile(file: java.io.File): Uri {
            return Uri(file.toURI().toString(), null)
        }
    }
}
