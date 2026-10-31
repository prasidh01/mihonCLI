package android.os

object SystemClock {
    @JvmStatic
    fun elapsedRealtime(): Long {
        return System.nanoTime() / 1_000_000L
    }

    @JvmStatic
    fun uptimeMillis(): Long {
        return System.nanoTime() / 1_000_000L
    }

    @JvmStatic
    fun currentThreadTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    @JvmStatic
    fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
