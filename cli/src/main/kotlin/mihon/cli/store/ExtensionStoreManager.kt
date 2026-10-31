package mihon.cli.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.GZIPInputStream

class ExtensionStoreManager(
    val baseDir: File = File(System.getProperty("user.home"), ".local/share/mihon"),
    private val client: OkHttpClient = OkHttpClient(),
) {
    val extensionsDir: File = File(baseDir, "extensions").apply { mkdirs() }
    val cacheDir: File = File(baseDir, "cache").apply { mkdirs() }
    private val indexFile: File = File(cacheDir, "index.pb")
    private val installedFile: File = File(baseDir, "installed_extensions.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    var defaultRepoUrl: String = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"

    @OptIn(ExperimentalSerializationApi::class)
    fun fetchIndex(forceRefresh: Boolean = false): KeiyoushiRepo {
        if (!forceRefresh && indexFile.exists() && System.currentTimeMillis() - indexFile.lastModified() < 3600_000) {
            try {
                return parseIndexFile(indexFile)
            } catch (_: Exception) {}
        }

        println("Fetching extensions index from Keiyoushi...")
        val request = Request.Builder().url(defaultRepoUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download index: HTTP ${response.code}")
            }
            val bodyBytes = response.body.bytes()
            indexFile.writeBytes(bodyBytes)
        }
        return parseIndexFile(indexFile)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun parseIndexFile(file: File): KeiyoushiRepo {
        val rawBytes = file.readBytes()
        val isGzip = rawBytes.size >= 2 && rawBytes[0] == 0x1f.toByte() && rawBytes[1] == 0x8b.toByte()
        val decompressed = if (isGzip) {
            GZIPInputStream(rawBytes.inputStream()).use { it.readBytes() }
        } else {
            rawBytes
        }
        return ProtoBuf.decodeFromByteArray<KeiyoushiRepo>(decompressed)
    }

    fun searchAvailable(query: String? = null, lang: String? = null): List<KeiyoushiRepo.Extension> {
        val repo = fetchIndex(forceRefresh = false)
        var list = repo.extensionList?.extensions.orEmpty()
        if (!query.isNullOrBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { ext ->
                ext.name.lowercase().contains(q) ||
                    ext.packageName.lowercase().contains(q) ||
                    ext.sources.any { it.name.lowercase().contains(q) }
            }
        }
        if (!lang.isNullOrBlank()) {
            val l = lang.trim().lowercase()
            list = list.filter { ext ->
                ext.sources.any { it.language.lowercase() == l || it.language == "all" }
            }
        }
        return list
    }

    fun downloadExtension(pkgName: String): File {
        val repo = fetchIndex()
        val ext = repo.extensionList?.extensions?.find { it.packageName == pkgName }
            ?: throw IllegalArgumentException("Extension '$pkgName' not found in store")

        val apkUrl = ext.resources.apkUrl
        val targetFile = File(extensionsDir, "$pkgName.apk")
        println("Downloading ${ext.name} ($pkgName) from $apkUrl...")

        val request = Request.Builder().url(apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }
            targetFile.writeBytes(response.body.bytes())
        }

        markInstalled(pkgName, ext.versionName, ext.versionCode)
        println("Successfully downloaded: ${targetFile.name}")
        return targetFile
    }

    fun getInstalledApks(): List<File> {
        return extensionsDir.listFiles { _, name -> name.endsWith(".apk") }?.toList().orEmpty()
    }

    private fun markInstalled(pkgName: String, versionName: String, versionCode: Long) {
        val map = getInstalledMap().toMutableMap()
        map[pkgName] = InstalledInfo(pkgName, versionName, versionCode, System.currentTimeMillis())
        installedFile.writeText(json.encodeToString(map))
    }

    fun getInstalledMap(): Map<String, InstalledInfo> {
        if (!installedFile.exists()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, InstalledInfo>>(installedFile.readText())
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun uninstallExtension(pkgName: String): Boolean {
        val target = File(extensionsDir, "$pkgName.apk")
        val deleted = target.delete()
        val map = getInstalledMap().toMutableMap()
        map.remove(pkgName)
        installedFile.writeText(json.encodeToString(map))
        return deleted
    }

    @kotlinx.serialization.Serializable
    data class InstalledInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val installedAt: Long,
    )
}
