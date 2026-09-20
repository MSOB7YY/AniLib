package com.revolgenx.anilib.media.data.watch.site

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.revolgenx.anilib.media.data.model.MediaWatchModel
import com.revolgenx.anilib.media.data.watch.NekoBt
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant

/**
 * nekoBT keys its series by its own id, an AniList id is not accepted by the search. The json api
 * does return the AniList link of whatever it matched, so a title search is enough to map the two.
 */
object NekoBtSiteResolver : WatchSiteResolver {

    override val siteKey: String = NekoBt.SITE_KEY
    override val idParam: String = "media_id"

    override fun resolve(client: OkHttpClient, media: MediaWatchModel): WatchSiteMedia? {
        val title = media.title?.romaji ?: media.title?.english ?: return null
        val encoded = URLEncoder.encode(title, "UTF-8")

        val search = get(client, "${NekoBt.API_URL}?query=$encoded&limit=1") ?: return null
        val data = search.getAsJsonObject("data") ?: return null

        val candidates = mutableListOf<JsonObject>()
        (data.get("media") as? JsonObject)?.let { candidates += it }
        (data.get("recommended_media") as? JsonObject)?.let { candidates += it }
        (data.get("similar_media") as? JsonArray)?.forEach { element ->
            (element as? JsonObject)?.let { candidates += it }
        }

        val matched = candidates.firstOrNull { matches(it, media) } ?: return null
        val siteMediaId = matched.string("id") ?: return null

        return WatchSiteMedia(
            siteKey = siteKey,
            anilistId = media.mediaId,
            siteMediaId = siteMediaId,
            episodes = episodes(client, siteMediaId)
        )
    }

    private fun matches(candidate: JsonObject, media: MediaWatchModel): Boolean {
        val anilist = candidate.getAsJsonObject("anilist") ?: return false
        if (anilist.int("display_id") == media.mediaId) return true

        val primary = anilist.getAsJsonObject("primary") ?: return false
        if (primary.int("id") == media.mediaId) return true
        return media.idMal != null && primary.int("id_mal") == media.idMal
    }

    override fun episodes(
        client: OkHttpClient,
        siteMediaId: String
    ): Map<Int, WatchSiteEpisode> {
        val response = get(client, "${NekoBt.API_URL}?media_id=$siteMediaId&limit=1")
            ?: return emptyMap()
        val episodes = response.getAsJsonObject("data")
            ?.getAsJsonObject("media")
            ?.getAsJsonArray("episodes")
            ?: return emptyMap()

        return episodes.mapNotNull { element ->
            val episode = element as? JsonObject ?: return@mapNotNull null
            val number = episode.int("absolute") ?: episode.int("episode") ?: return@mapNotNull null
            val id = episode.string("id") ?: return@mapNotNull null

            number to WatchSiteEpisode(
                id = id,
                number = number,
                title = episode.string("title"),
                airingAt = episode.string("airDateUtc")?.let { date ->
                    runCatching { Instant.parse(date).epochSecond }.getOrNull()
                }
            )
        }.toMap()
    }

    private fun get(client: OkHttpClient, url: String): JsonObject? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            JsonParser.parseString(response.body?.string().orEmpty()) as? JsonObject
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? = runCatching {
        get(key)?.takeIf { !it.isJsonNull }?.asString
    }.getOrNull()

    private fun JsonObject.int(key: String): Int? = runCatching {
        get(key)?.takeIf { !it.isJsonNull }?.asInt
    }.getOrNull()
}
