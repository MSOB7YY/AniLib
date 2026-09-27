// all mal covers logic by claude
package com.revolgenx.anilib.media.data.cover

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.util.SparseIntArray
import android.util.SparseLongArray
import com.google.gson.JsonParser
import com.revolgenx.anilib.type.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

// -- record: int key (idMal shl 1 or isManga), long entry (day shl 48 or folder shl 32 or image)
// -- path (folder + image) is 0 when the media has no cover on mal
object MalCovers {

    private const val JIKAN_URL = "https://api.jikan.moe/v4/"
    private const val CDN_URL = "https://cdn.myanimelist.net/images/"
    private const val CDN_MARKER = "/images/"
    private const val ANIME = "anime/"
    private const val MANGA = "manga/"
    private const val SUFFIX = ".webp"
    private const val LARGE_SUFFIX = "l.webp"

    private const val FILE_NAME = "mal_covers.bin"
    private const val TEMP_SUFFIX = ".tmp"
    private const val RECORD_SIZE = 12

    private const val DAY_MILLIS = 86_400_000L
    private const val FOUND_TTL_DAYS = 30
    private const val MISSING_TTL_DAYS = 3

    private const val DAY_SHIFT = 48
    private const val FOLDER_SHIFT = 32
    private const val PATH_MASK = (1L shl DAY_SHIFT) - 1
    private const val IMAGE_MASK = (1L shl FOLDER_SHIFT) - 1
    private const val MAX_FOLDER = 0xFFFFL
    private const val MAX_IMAGE = 0xFFFFFFFFL

    private const val INITIAL_QUEUE_CAPACITY = 64
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY = 1000L
    private const val REQUESTS_PER_SECOND = 3
    private const val REQUESTS_PER_MINUTE = 60
    private const val SECOND_MILLIS = 1000L
    private const val MINUTE_MILLIS = 60_000L
    private const val RATE_LIMITED_DELAY = 2000L

    private const val FAILED = -1L
    private const val RATE_LIMITED = -2L
    private const val UNAVAILABLE = -3L

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var file: File
    private lateinit var client: OkHttpClient

    private var entries: SparseLongArray? = null
    private val pending = SparseArray<ArrayList<Listener>>()

    private val failures = SparseIntArray()
    private val refreshed = SparseBooleanArray()

    private var queue = IntArray(INITIAL_QUEUE_CAPACITY)
    private var queueSize = 0
    private var draining = false

    private val requestTimes = LongArray(REQUESTS_PER_MINUTE)
    private var requestIndex = 0

    fun init(context: Context, client: OkHttpClient) {
        file = File(context.filesDir, FILE_NAME)
        this.client = client
        thread(name = "mal-covers-load") { synchronized(lock) { entries() } }
    }

    fun key(idMal: Int?, type: MediaType?): Int {
        if (idMal == null || idMal <= 0) return 0
        return when (type) {
            MediaType.ANIME -> idMal shl 1
            MediaType.MANGA -> idMal shl 1 or 1
            else -> 0
        }
    }

    fun path(key: Int): Long {
        val entry = synchronized(lock) { entries().get(key) }
        return entry and PATH_MASK
    }

    fun url(key: Int, path: Long, large: Boolean): String {
        val type = typeOf(key)
        val folder = path ushr FOLDER_SHIFT
        val image = path and IMAGE_MASK
        val suffix = if (large) LARGE_SUFFIX else SUFFIX
        return StringBuilder(64)
            .append(CDN_URL)
            .append(type)
            .append(folder)
            .append('/')
            .append(image)
            .append(suffix)
            .toString()
    }

    fun isFresh(key: Int): Boolean {
        val entry = synchronized(lock) { entries().get(key) }
        if (entry == 0L) return false
        val isMissing = entry and PATH_MASK == 0L
        val ttlDays = if (isMissing) MISSING_TTL_DAYS else FOUND_TTL_DAYS
        val resolvedDay = entry ushr DAY_SHIFT
        return today() - resolvedDay < ttlDays
    }

    fun resolve(key: Int, listener: Listener) {
        synchronized(lock) {
            var listeners = pending.get(key)
            if (listeners == null) {
                listeners = ArrayList(2)
                pending.put(key, listeners)
                push(key)
            }
            listeners.add(listener)

            if (draining) return
            draining = true
        }
        thread(name = "mal-covers-resolve", block = ::drain)
    }

    fun refresh(key: Int, listener: Listener): Boolean {
        synchronized(lock) {
            if (refreshed.get(key)) return false
            refreshed.put(key, true)
        }
        resolve(key, listener)
        return true
    }

    private fun drain() {
        var output: FileOutputStream? = null
        val record = ByteBuffer.allocate(RECORD_SIZE)
        try {
            while (true) {
                val key = synchronized(lock) {
                    if (queueSize == 0) {
                        draining = false
                        return
                    }
                    pop()
                }

                throttle()
                val path = fetch(key)
                if (path == RATE_LIMITED) {
                    SystemClock.sleep(RATE_LIMITED_DELAY)
                    synchronized(lock) { if (pending.get(key) != null) push(key) }
                    continue
                }
                if (path == UNAVAILABLE) {
                    val canRetry = synchronized(lock) { retry(key) }
                    if (canRetry) {
                        SystemClock.sleep(RETRY_DELAY)
                        continue
                    }
                }
                if (path == FAILED || path == UNAVAILABLE) {
                    val failed = synchronized(lock) { take(key) }
                    notifyEnded(failed, false)
                    continue
                }

                var listeners: ArrayList<Listener>? = null
                var isChanged = false
                val entry = today() shl DAY_SHIFT or path
                synchronized(lock) {
                    listeners = take(key)
                    val entries = entries()
                    val oldPath = entries.get(key) and PATH_MASK
                    isChanged = path != 0L && path != oldPath
                    entries.put(key, entry)
                }

                try {
                    val stream = output ?: FileOutputStream(file, true).also { output = it }
                    record.clear()
                    record.putInt(key).putLong(entry)
                    stream.write(record.array())
                } catch (e: IOException) {
                    // -- entry stays in memory, resolved again next start
                }

                notifyEnded(listeners, isChanged)
            }
        } finally {
            try {
                output?.close()
            } catch (e: IOException) {
            }
        }
    }

    private fun retry(key: Int): Boolean {
        val failureCount = failures.get(key) + 1
        if (failureCount >= MAX_ATTEMPTS) return false
        failures.put(key, failureCount)
        pushLast(key)
        return true
    }

    private fun take(key: Int): ArrayList<Listener>? {
        val listeners = pending.get(key)
        pending.remove(key)
        failures.delete(key)
        return listeners
    }

    private fun notifyEnded(listeners: ArrayList<Listener>?, isChanged: Boolean) {
        if (listeners == null) return
        mainHandler.post {
            for (listener in listeners) listener.onCoverResolveEnded(isChanged)
        }
    }

    private fun throttle() {
        val now = SystemClock.elapsedRealtime()
        val secondSlot =
            (requestIndex + REQUESTS_PER_MINUTE - REQUESTS_PER_SECOND) % REQUESTS_PER_MINUTE
        val secondWait = requestTimes[secondSlot] + SECOND_MILLIS - now
        val minuteWait = requestTimes[requestIndex] + MINUTE_MILLIS - now
        val wait = maxOf(secondWait, minuteWait)
        if (wait > 0) SystemClock.sleep(wait)

        requestTimes[requestIndex] = SystemClock.elapsedRealtime()
        requestIndex = (requestIndex + 1) % REQUESTS_PER_MINUTE
    }

    private fun fetch(key: Int): Long {
        val idMal = key ushr 1
        val url = JIKAN_URL + typeOf(key) + idMal
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> 0L
                    response.code == 429 -> RATE_LIMITED
                    !response.isSuccessful -> UNAVAILABLE
                    else -> {
                        val body = response.body?.string()
                        val imageUrl = imageUrl(body)
                        if (imageUrl == null) UNAVAILABLE else parsePath(imageUrl)
                    }
                }
            }
        } catch (e: IOException) {
            FAILED
        }
    }

    private fun imageUrl(body: String?): String? = try {
        JsonParser.parseString(body.orEmpty()).asJsonObject
            .getAsJsonObject("data")
            ?.getAsJsonObject("images")
            ?.getAsJsonObject("jpg")
            ?.get("image_url")
            ?.takeIf { !it.isJsonNull }
            ?.asString
    } catch (e: RuntimeException) {
        null
    }

    // -- .../images/anime/1208/94745.jpg, the no cover placeholder has another layout
    private fun parsePath(url: String): Long {
        val marker = url.indexOf(CDN_MARKER)
        if (marker < 0) return 0L
        var index = url.indexOf('/', marker + CDN_MARKER.length) + 1
        if (index == 0) return 0L

        var folder = 0L
        while (index < url.length && url[index] in '0'..'9' && folder <= MAX_FOLDER) {
            folder = folder * 10 + (url[index] - '0')
            index++
        }
        if (folder == 0L || folder > MAX_FOLDER) return 0L
        if (index >= url.length || url[index] != '/') return 0L
        index++

        var image = 0L
        while (index < url.length && url[index] in '0'..'9' && image <= MAX_IMAGE) {
            image = image * 10 + (url[index] - '0')
            index++
        }
        if (image == 0L || image > MAX_IMAGE) return 0L
        if (index >= url.length || url[index] != '.') return 0L

        return folder shl FOLDER_SHIFT or image
    }

    private fun typeOf(key: Int) = if (key and 1 == 0) ANIME else MANGA

    private fun push(key: Int) {
        if (queueSize == queue.size) queue = queue.copyOf(queueSize * 2)
        queue[queueSize] = key
        queueSize++
    }

    private fun pushLast(key: Int) {
        if (queueSize == queue.size) queue = queue.copyOf(queueSize * 2)
        System.arraycopy(queue, 0, queue, 1, queueSize)
        queue[0] = key
        queueSize++
    }

    private fun pop(): Int {
        queueSize--
        return queue[queueSize]
    }

    private fun entries(): SparseLongArray = entries ?: load().also { entries = it }

    private fun load(): SparseLongArray {
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            return SparseLongArray()
        }

        val count = bytes.size / RECORD_SIZE
        val loaded = SparseLongArray(count)
        val buffer = ByteBuffer.wrap(bytes)
        repeat(count) { loaded.put(buffer.int, buffer.long) }

        val isTruncated = bytes.size % RECORD_SIZE != 0
        val maxRecords = loaded.size() + loaded.size() / 4
        if (isTruncated || count > maxRecords) compact(loaded)
        return loaded
    }

    private fun compact(entries: SparseLongArray) {
        val buffer = ByteBuffer.allocate(entries.size() * RECORD_SIZE)
        for (index in 0 until entries.size()) {
            buffer.putInt(entries.keyAt(index)).putLong(entries.valueAt(index))
        }

        val temp = File(file.path + TEMP_SUFFIX)
        try {
            temp.writeBytes(buffer.array())
            if (!temp.renameTo(file)) temp.delete()
        } catch (e: IOException) {
            temp.delete()
        }
    }

    private fun today() = System.currentTimeMillis() / DAY_MILLIS

    interface Listener {
        fun onCoverResolveEnded(isChanged: Boolean)
    }
}
