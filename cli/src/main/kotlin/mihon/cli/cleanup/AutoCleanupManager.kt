package mihon.cli.cleanup

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AutoCleanupManager {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "mihon-cleanup").apply { isDaemon = true }
    }

    /**
     * Schedules deletion of a file or directory [delaySeconds] after invocation.
     * Uses a detached background shell process so deletion occurs even if the JVM process terminates.
     */
    fun scheduleDeletion(target: File, delaySeconds: Long = 300) {
        if (!target.exists()) return

        val path = target.absolutePath
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true

        if (!isWindows) {
            try {
                // Spawn detached subshell that waits and deletes independently of JVM lifecycle
                val pb = ProcessBuilder(
                    "bash", "-c",
                    "(nohup bash -c 'sleep $delaySeconds && rm -rf \"$path\"' >/dev/null 2>&1 &)"
                )
                pb.start()
            } catch (_: Exception) {
                fallbackSchedule(target, delaySeconds)
            }
        } else {
            try {
                val pb = ProcessBuilder(
                    "cmd.exe", "/c",
                    "start /b cmd /c timeout /t $delaySeconds /nobreak >nul & rmdir /s /q \"$path\""
                )
                pb.start()
            } catch (_: Exception) {
                fallbackSchedule(target, delaySeconds)
            }
        }
    }

    private fun fallbackSchedule(target: File, delaySeconds: Long) {
        scheduler.schedule({
            try {
                if (target.exists()) {
                    target.deleteRecursively()
                }
            } catch (_: Exception) {}
        }, delaySeconds, TimeUnit.SECONDS)
    }

    /**
     * Cleans up orphaned reader cache files older than [maxAgeMs] (default: 30 minutes).
     */
    fun cleanOrphanedCache(readerCacheDir: File, maxAgeMs: Long = 1800_000) {
        if (!readerCacheDir.exists() || !readerCacheDir.isDirectory) return
        val now = System.currentTimeMillis()
        try {
            readerCacheDir.listFiles()?.forEach { mangaDir ->
                if (mangaDir.isDirectory) {
                    mangaDir.listFiles()?.forEach { chDir ->
                        if (now - chDir.lastModified() > maxAgeMs) {
                            chDir.deleteRecursively()
                        }
                    }
                    if (mangaDir.list()?.isEmpty() == true) {
                        mangaDir.delete()
                    }
                } else if (now - mangaDir.lastModified() > maxAgeMs) {
                    mangaDir.delete()
                }
            }
        } catch (_: Exception) {}
    }
}
