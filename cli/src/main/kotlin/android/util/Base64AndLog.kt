package android.util

import java.nio.charset.StandardCharsets

object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        val encoder = if ((flags and URL_SAFE) != 0) {
            java.util.Base64.getUrlEncoder()
        } else {
            java.util.Base64.getEncoder()
        }
        val encoded = encoder.encode(input)
        return String(encoded, StandardCharsets.UTF_8)
    }

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray {
        val encoder = if ((flags and URL_SAFE) != 0) {
            java.util.Base64.getUrlEncoder()
        } else {
            java.util.Base64.getEncoder()
        }
        return encoder.encode(input)
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val decoder = if ((flags and URL_SAFE) != 0) {
            java.util.Base64.getUrlDecoder()
        } else {
            java.util.Base64.getDecoder()
        }
        val cleanStr = str.replace("\n", "").replace("\r", "").trim()
        return decoder.decode(cleanStr)
    }

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray {
        return decode(String(input, StandardCharsets.UTF_8), flags)
    }
}

object Log {
    @JvmStatic fun d(tag: String, msg: String): Int { println("[$tag][DEBUG] $msg"); return 0 }
    @JvmStatic fun i(tag: String, msg: String): Int { println("[$tag][INFO] $msg"); return 0 }
    @JvmStatic fun w(tag: String, msg: String): Int { println("[$tag][WARN] $msg"); return 0 }
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable?): Int { println("[$tag][WARN] $msg: ${tr?.message}"); return 0 }
    @JvmStatic fun e(tag: String, msg: String): Int { System.err.println("[$tag][ERROR] $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int { System.err.println("[$tag][ERROR] $msg: ${tr?.message}"); tr?.printStackTrace(); return 0 }
}
