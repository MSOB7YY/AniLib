package com.revolgenx.anilib.media.data.watch

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Turns a site search response into results the app can list without opening a browser. */
interface WatchInlineEngine {
    val siteKey: String
    val apiUrl: String
    fun parse(body: String): List<WatchTorrentModel>
}

object WatchInlineEngines {
    private val engines = listOf<WatchInlineEngine>(NekoBtInlineEngine)

    fun of(siteKey: String?): WatchInlineEngine? =
        siteKey?.let { key -> engines.firstOrNull { it.siteKey == key } }
}

object NekoBtInlineEngine : WatchInlineEngine {
    override val siteKey: String = NekoBt.SITE_KEY
    override val apiUrl: String = NekoBt.API_URL

    override fun parse(body: String): List<WatchTorrentModel> {
        val root = JsonParser.parseString(body) as? JsonObject ?: return emptyList()
        val data = root.getAsJsonObject("data") ?: return emptyList()
        val results = data.getAsJsonArray("results") ?: return emptyList()
        val preferredGroups = WatchPreference.preferredGroups

        return results.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id") ?: return@mapNotNull null
            val group = item.groupName()

            WatchTorrentModel(
                id = id,
                title = item.string("title").orEmpty(),
                magnet = item.string("magnet"),
                webUrl = NekoBt.TORRENT_URL + id,
                torrentUrl = NekoBt.torrentFileUrl(id),
                size = item.string("filesize")?.toLongOrNull() ?: 0,
                seeders = item.string("seeders")?.toIntOrNull() ?: 0,
                leechers = item.string("leechers")?.toIntOrNull() ?: 0,
                completed = item.string("completed")?.toIntOrNull() ?: 0,
                uploadedAt = item.string("uploaded_at")?.toLongOrNull() ?: 0,
                batch = item.bool("batch"),
                group = group,
                quality = item.quality(),
                audioLanguages = item.languages("audio_lang"),
                subLanguages = item.languages("sub_lang") + item.languages("fsub_lang"),
                hardsub = item.bool("hardsub"),
                preferred = group != null && preferredGroups.contains(group.lowercase())
            )
        }
    }

    private fun JsonObject.string(key: String): String? = runCatching {
        get(key)?.takeIf { !it.isJsonNull }?.asString
    }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean = runCatching {
        get(key)?.takeIf { !it.isJsonNull }?.asBoolean
    }.getOrNull() ?: false

    private fun JsonObject.int(key: String): Int? = runCatching {
        get(key)?.takeIf { !it.isJsonNull }?.asInt
    }.getOrNull()

    private fun JsonObject.groupName(): String? {
        val groups = get("groups") as? JsonArray ?: return null
        return groups.firstNotNullOfOrNull { (it as? JsonObject)?.string("display_name") }
    }

    private fun JsonObject.languages(key: String): List<String> =
        string(key)?.split(",")?.mapNotNull { it.trim().takeIf { code -> code.isNotEmpty() } }
            .orEmpty()

    private fun JsonObject.quality(): String? {
        val type = int("video_type")?.let { NekoBt.videoTypeLabels[it] }
        val codec = int("video_codec")?.let { NekoBt.codecLabels[it] }

        return listOfNotNull(type, codec).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
