package com.revolgenx.anilib.common.repository.network

import java.io.File
import java.io.IOException

/**
 * Plain file cache for graphql responses. AniList is queried over POST, which okhttp's own cache
 * never stores, so the last good body of every query is kept here to be replayed when the network
 * is unreachable.
 */
class OfflineResponseCache(private val directory: File, private val maxSize: Long) {

    companion object {
        private const val TEMP_SUFFIX = ".tmp"
    }

    private val lock = Any()

    /** -1 until the directory has been measured once, then kept up to date by [write]. */
    private var size = -1L

    fun read(key: String): ByteArray? {
        val file = File(directory, key)
        if (!file.isFile) return null
        return try {
            file.readBytes().also { file.setLastModified(System.currentTimeMillis()) }
        } catch (e: IOException) {
            null
        }
    }

    /** The body is written outside the lock so a slow write never blocks other responses. */
    fun write(key: String, body: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs()) return

        val temp = File(directory, "$key.${System.nanoTime()}$TEMP_SUFFIX")
        try {
            temp.writeBytes(body)
        } catch (e: IOException) {
            temp.delete()
            return
        }

        synchronized(lock) {
            val file = File(directory, key)
            val replaced = if (file.isFile) file.length() else 0L
            val total = measure()
            if (!temp.renameTo(file)) {
                temp.delete()
                return
            }
            size = total - replaced + body.size
            trim()
        }
    }

    private fun entries() = directory.listFiles { file -> !file.name.endsWith(TEMP_SUFFIX) }

    private fun measure(): Long {
        if (size < 0) size = entries()?.sumOf { it.length() } ?: 0L
        return size
    }

    /** Drops the least recently read entries until the cache is comfortably back under its cap. */
    private fun trim() {
        if (size <= maxSize) return
        val files = entries()?.sortedBy { it.lastModified() } ?: return
        val target = maxSize / 10 * 9
        var total = size
        for (file in files) {
            if (total <= target) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        size = total
    }
}
