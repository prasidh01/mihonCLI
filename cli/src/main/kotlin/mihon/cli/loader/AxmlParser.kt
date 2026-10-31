package mihon.cli.loader

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedManifest(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceClasses: List<String>,
    val extensionName: String,
)

object AxmlParser {

    fun parse(stream: InputStream): ParsedManifest {
        val bytes = stream.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = buffer.int
        if (magic != 0x00080003) {
            throw IllegalArgumentException("Not an Android Binary XML file")
        }
        val fileSize = buffer.int

        val strings = mutableListOf<String>()
        var packageName = ""
        var versionName = ""
        var versionCode = 0L
        val metaData = mutableMapOf<String, String>()

        while (buffer.hasRemaining()) {
            val chunkStart = buffer.position()
            val chunkType = buffer.int
            val chunkSize = buffer.int

            when (chunkType) {
                0x001c0001 -> { // String Pool
                    val stringCount = buffer.int
                    val styleCount = buffer.int
                    val flags = buffer.int
                    val stringsStart = buffer.int
                    val stylesStart = buffer.int

                    val isUtf8 = (flags and (1 shl 8)) != 0
                    val offsets = IntArray(stringCount) { buffer.int }

                    val poolStart = chunkStart + stringsStart
                    for (off in offsets) {
                        buffer.position(poolStart + off)
                        if (isUtf8) {
                            var u16len = buffer.get().toInt() and 0xFF
                            if ((u16len and 0x80) != 0) {
                                u16len = ((u16len and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                            }
                            var u8len = buffer.get().toInt() and 0xFF
                            if ((u8len and 0x80) != 0) {
                                u8len = ((u8len and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                            }
                            val strBytes = ByteArray(u8len)
                            buffer.get(strBytes)
                            strings.add(String(strBytes, Charsets.UTF_8))
                        } else {
                            val u16len = buffer.short.toInt() and 0xFFFF
                            val strBytes = ByteArray(u16len * 2)
                            buffer.get(strBytes)
                            strings.add(String(strBytes, Charsets.UTF_16LE))
                        }
                    }
                }

                0x00100102 -> { // START_TAG
                    val lineNumber = buffer.int
                    val comment = buffer.int
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val attrStart = buffer.short.toInt() and 0xFFFF
                    val attrSize = buffer.short.toInt() and 0xFFFF
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    val idIndex = buffer.short
                    val classIndex = buffer.short
                    val styleIndex = buffer.short

                    val tagName = if (nameIdx in strings.indices) strings[nameIdx] else ""
                    val attrs = mutableMapOf<String, String>()

                    for (i in 0 until attrCount) {
                        val aNs = buffer.int
                        val aName = buffer.int
                        val aRawVal = buffer.int
                        val aType = buffer.int
                        val aData = buffer.int

                        val attrName = if (aName in strings.indices) strings[aName] else ""
                        val attrVal = if (aRawVal in strings.indices) strings[aRawVal] else aData.toString()
                        attrs[attrName] = attrVal
                    }

                    if (tagName == "manifest") {
                        attrs["package"]?.let { packageName = it }
                        attrs["versionName"]?.let { versionName = it }
                        attrs["versionCode"]?.toLongOrNull()?.let { versionCode = it }
                    } else if (tagName == "meta-data") {
                        val mName = attrs["name"]
                        val mVal = attrs["value"] ?: attrs["resource"]
                        if (mName != null && mVal != null) {
                            metaData[mName] = mVal
                        }
                    }
                }
            }

            buffer.position(chunkStart + chunkSize)
        }

        val rawClasses = metaData["tachiyomi.extension.class"]
            ?: metaData["tachiyomi.extension.factory"]
            ?: ""
        val sourceClasses = rawClasses.split(";").map { it.trim() }.filter { it.isNotEmpty() }.map {
            if (it.startsWith(".")) "$packageName$it" else it
        }
        val extName = metaData["tachiyomix.name"] ?: metaData["name"] ?: packageName

        return ParsedManifest(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            sourceClasses = sourceClasses,
            extensionName = extName,
        )
    }
}
