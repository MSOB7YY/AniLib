package com.revolgenx.anilib.media.data.watch

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.revolgenx.anilib.common.preference.load
import com.revolgenx.anilib.common.preference.save
import com.revolgenx.anilib.type.MediaType

object WatchActionStore {

    private const val WATCH_ACTION_KEY = "WATCH_ACTION_KEY"
    private const val WATCH_ACTION_SEED_KEY = "WATCH_ACTION_SEED_KEY"
    private const val SEED_VERSION = 1

    private val gson = Gson()
    private val type = object : TypeToken<List<WatchAction>>() {}.type

    private var cache: List<WatchAction>? = null

    fun all(): List<WatchAction> {
        cache?.let { return it }

        if (load(WATCH_ACTION_SEED_KEY, 0) < SEED_VERSION) {
            val seeded = seeds()
            persist(seeded)
            save(WATCH_ACTION_SEED_KEY, SEED_VERSION)
            return seeded
        }

        val stored = load(WATCH_ACTION_KEY, null as String?)
        val loaded = runCatching { stored?.let { gson.fromJson<List<WatchAction>>(it, type) } }
            .getOrNull()
            ?.sortedBy { it.order }
            ?: seeds()

        cache = loaded
        return loaded
    }

    fun forMedia(mediaType: Int?, episodeScoped: Boolean): List<WatchAction> =
        all().filter { it.appliesTo(episodeScoped, mediaType) }

    fun find(id: String): WatchAction? = all().firstOrNull { it.id == id }

    fun saveAll(actions: List<WatchAction>) {
        persist(actions.mapIndexed { index, action -> action.copy(order = index) })
    }

    fun upsert(action: WatchAction) {
        val current = all().toMutableList()
        val index = current.indexOfFirst { it.id == action.id }

        if (index >= 0) current[index] = action else current += action.copy(order = current.size)

        val cleared = if (action.isDefault) {
            current.map { if (it.id == action.id) it else it.copy(isDefault = false) }
        } else {
            current
        }

        saveAll(cleared)
    }

    fun delete(id: String) {
        saveAll(all().filterNot { it.id == id })
    }

    fun restoreDefaults() {
        persist(seeds())
    }

    fun import(json: String): Boolean {
        val imported = runCatching { gson.fromJson<List<WatchAction>>(json, type) }.getOrNull()
            ?: return false
        if (imported.isEmpty()) return false

        saveAll(imported)
        return true
    }

    fun export(): String = gson.toJson(all(), type)

    private fun persist(actions: List<WatchAction>) {
        cache = actions
        save(WATCH_ACTION_KEY, gson.toJson(actions, type))
    }

    private fun seeds(): List<WatchAction> {
        val anime = MediaType.ANIME.ordinal

        // no site id, the title carries the search, and the episode number when one is targeted
        val nekoBtQuery =
            "{!site.nekobt}{media.title.romaji} {/}{!site.nekobt.episode}{?episode}{episode|pad2}{/}{/}"

        val nekoBtParams = listOf(
            WatchActionParam("sort_by", """{preset.sort ?? "latest"}"""),
            WatchActionParam("media_id", "{site.nekobt}"),
            WatchActionParam("episode_ids", "{site.nekobt.episode}"),
            WatchActionParam("query", nekoBtQuery),
            WatchActionParam("limit", """{preset.limit ?? "50"}""")
        )

        return listOf(
            WatchAction(
                id = "nekobt-inline",
                name = "nekoBT",
                baseUrl = NekoBt.SEARCH_URL,
                params = nekoBtParams,
                vars = mapOf("sort" to "latest", "limit" to "50"),
                mediaType = anime,
                siteKey = NekoBt.SITE_KEY,
                inlineResults = true,
                isDefault = true,
                order = 0
            ),
            WatchAction(
                id = "nekobt-web",
                name = "nekoBT (browser)",
                baseUrl = NekoBt.SEARCH_URL,
                params = nekoBtParams,
                vars = mapOf("sort" to "latest", "limit" to "50"),
                mediaType = anime,
                siteKey = NekoBt.SITE_KEY,
                order = 1
            ),
            WatchAction(
                id = "nyaa",
                name = "Nyaa",
                baseUrl = "https://nyaa.si/",
                params = listOf(
                    WatchActionParam("q", "{media.title.romaji}{?episode} {episode|pad2}{/}"),
                    WatchActionParam("s", "seeders"),
                    WatchActionParam("o", "desc")
                ),
                mediaType = anime,
                order = 2
            )
        )
    }
}
