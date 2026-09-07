package com.revolgenx.anilib.common.repository.network

import com.revolgenx.anilib.common.preference.UserPreference
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.security.MessageDigest

/**
 * Keeps the last good body of every graphql query on disk and replays it when the request cannot
 * reach the server, so the app stays usable offline or on a flaky connection. The network is always
 * tried first, so a reachable server never serves stale data.
 */
class OfflineCacheInterceptor(private val cache: OfflineResponseCache) : Interceptor {

    companion object {
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        private val errorsToken = "\"errors\":[".toByteArray()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val payload = request.payload() ?: return chain.proceed(request)

        // mutations change server state, so a replayed response would be a lie
        if (payload.isMutation()) return chain.proceed(request)

        val key = request.cacheKey(payload)

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            return cached(request, key) ?: throw e
        }

        if (!response.isSuccessful) {
            return cached(request, key)?.also { response.close() } ?: response
        }

        val body = response.body ?: return response
        val contentType = body.contentType()
        val bytes = try {
            body.bytes()
        } catch (e: IOException) {
            return cached(request, key) ?: throw e
        }

        // graphql reports failures with a 200, and a cached failure would outlive the outage
        if (!bytes.hasErrors()) {
            cache.write(key, bytes)
        }
        OfflineCacheState.report(fromCache = false)

        return response.newBuilder().body(bytes.toResponseBody(contentType)).build()
    }

    private fun cached(request: Request, key: String): Response? {
        val bytes = cache.read(key) ?: return null
        OfflineCacheState.report(fromCache = true)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("offline cache")
            .body(bytes.toResponseBody(jsonMediaType))
            .build()
    }

    private fun Request.payload(): String? {
        val body = body ?: return null
        if (body.isOneShot() || body.isDuplex()) return null
        return try {
            Buffer().also { body.writeTo(it) }.readUtf8()
        } catch (e: IOException) {
            null
        }
    }

    /** Scoped to the user, so signing out or switching accounts cannot surface someone else's data. */
    private fun Request.cacheKey(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${UserPreference.userId}|$url|$payload".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.isMutation(): Boolean {
        val field = indexOf("\"query\"")
        if (field < 0) return false
        val separator = indexOf(':', field)
        if (separator < 0) return false
        val document = indexOf('"', separator + 1)
        return document >= 0 && startsWith("mutation", document + 1)
    }

    /**
     * Looks for a top level `errors` key without decoding the body, which can be several megabytes.
     * Anything of that shape inside the data would be escaped, so an unescaped match can only come
     * from the response envelope.
     */
    private fun ByteArray.hasErrors(): Boolean {
        val token = errorsToken
        if (size < token.size) return false
        outer@ for (start in 0..size - token.size) {
            for (offset in token.indices) {
                if (this[start + offset] != token[offset]) continue@outer
            }
            return true
        }
        return false
    }
}
