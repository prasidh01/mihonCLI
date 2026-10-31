package mihon.cli.ui

import java.io.BufferedReader
import java.io.InputStreamReader

object TerminalUi {
    private val isAnsiSupported = System.getenv("NO_COLOR") == null &&
        System.getenv("TERM") != "dumb"

    // ANSI escape codes
    private const val RESET = "\u001B[0m"
    private const val BOLD = "\u001B[1m"
    private const val DIM = "\u001B[2m"
    private const val ITALIC = "\u001B[3m"
    private const val UNDERLINE = "\u001B[4m"

    private const val BLACK = "\u001B[30m"
    private const val RED = "\u001B[31m"
    private const val GREEN = "\u001B[32m"
    private const val YELLOW = "\u001B[33m"
    private const val BLUE = "\u001B[34m"
    private const val MAGENTA = "\u001B[35m"
    private const val CYAN = "\u001B[36m"
    private const val WHITE = "\u001B[37m"

    private const val BRIGHT_BLACK = "\u001B[90m"
    private const val BRIGHT_GREEN = "\u001B[92m"
    private const val BRIGHT_YELLOW = "\u001B[93m"
    private const val BRIGHT_CYAN = "\u001B[96m"

    private val reader = BufferedReader(InputStreamReader(System.`in`))

    fun color(text: String, code: String): String = if (isAnsiSupported) "$code$text$RESET" else text
    fun bold(text: String): String = color(text, BOLD)
    fun cyan(text: String): String = color(text, CYAN)
    fun brightCyan(text: String): String = color(text, BRIGHT_CYAN)
    fun green(text: String): String = color(text, GREEN)
    fun brightGreen(text: String): String = color(text, BRIGHT_GREEN)
    fun yellow(text: String): String = color(text, YELLOW)
    fun brightYellow(text: String): String = color(text, BRIGHT_YELLOW)
    fun dim(text: String): String = color(text, DIM)
    fun red(text: String): String = color(text, RED)
    fun magenta(text: String): String = color(text, MAGENTA)

    fun banner() {
        val art = """
   __  ____ __              ________    ____
  /  |/  (_) /_  ____  ____/ / ____/   /  _/
 / /|_/ / / __ \/ __ \/ __  / /       / /  
/ /  / / / / / / /_/ / /_/ / /___   _/ /   
/_/  /_/_/_/ /_/\____/\__,_/\____/  /___/   
"""
        println(brightCyan(art))
        println("  " + bold("Mihon CLI") + " - Native Manga Reader & Extension Engine")
        println("  " + dim("Powered by Keiyoushi Extension Repository"))
        divider()
    }

    fun header(title: String, subtitle: String? = null) {
        println()
        val line = "=".repeat(80)
        println(brightCyan(line))
        println("  " + bold(title))
        if (subtitle != null) {
            println("  " + dim(subtitle))
        }
        println(brightCyan(line))
    }

    fun subHeader(title: String) {
        println()
        println(cyan("--- $title ---"))
    }

    fun divider(char: Char = '-', length: Int = 80) {
        println(dim(char.toString().repeat(length)))
    }

    fun menuItem(key: String, title: String, subtitle: String? = null) {
        val keyPart = yellow("[") + bold(key) + yellow("]")
        if (subtitle != null) {
            println("  $keyPart $title ${dim("($subtitle)")}")
        } else {
            println("  $keyPart $title")
        }
    }

    fun status(msg: String) {
        println("  " + dim("• ") + msg)
    }

    fun success(msg: String) {
        println(green("✓ ") + bold(msg))
    }

    fun warn(msg: String) {
        println(yellow("! ") + msg)
    }

    fun error(msg: String) {
        println(red("✗ ") + bold(msg))
    }

    fun prompt(message: String): String? {
        print(message)
        System.out.flush()
        return try {
            val line = reader.readLine()
            line?.trim()
        } catch (_: Exception) {
            null
        }
    }

    fun promptChoice(message: String, default: String? = null): String {
        val suffix = if (default != null) " [$default]: " else ": "
        val input = prompt(cyan(message) + suffix) ?: return "q"
        if (input.isBlank()) {
            return default.orEmpty()
        }
        return input
    }

    fun promptConfirm(message: String, default: Boolean = true): Boolean {
        val hint = if (default) "[Y/n]" else "[y/N]"
        val input = prompt("$message $hint: ") ?: return false
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) return default
        return trimmed == "y" || trimmed == "yes"
    }

    fun promptInt(message: String, min: Int, max: Int, default: Int? = null): Int? {
        val suffix = if (default != null) " [$default]: " else ": "
        val input = prompt(message + suffix) ?: return null
        if (input.isBlank()) return default
        val num = input.toIntOrNull()
        if (num != null && num in min..max) return num
        return null
    }

    fun wrapText(text: String, maxWidth: Int = 80, indent: String = ""): String {
        val clean = text.replace(Regex("<[^>]*>"), "") // Strip HTML if present
        val words = clean.split(Regex("\\s+"))
        val sb = StringBuilder()
        var currentLength = 0
        sb.append(indent)
        for (w in words) {
            if (currentLength + w.length + 1 > maxWidth) {
                sb.append("\n").append(indent)
                currentLength = 0
            } else if (currentLength > 0) {
                sb.append(" ")
                currentLength += 1
            }
            sb.append(w)
            currentLength += w.length
        }
        return sb.toString()
    }
}
