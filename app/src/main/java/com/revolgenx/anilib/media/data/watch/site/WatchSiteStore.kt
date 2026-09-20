package com.revolgenx.anilib.media.data.watch.site

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.revolgenx.anilib.common.preference.load
import com.revolgenx.anilib.common.preference.save

/** Site ids are cheap to cache and expensive to resolve, a manual id always wins over a resolved one. */
object WatchSiteStore {

    private const val WATCH_SITE_MEDIA_KEY = "WATCH_SITE_MEDIA_KEY"

    private val gson = Gson()
    private val type = object : TypeToken<MutableMap<String, WatchSiteMedia>>() {}.type

    private var cache: MutableMap<String, WatchSiteMedia>? = null

    private fun all(): MutableMap<String, WatchSiteMedia> {
        cache?.let { return it }

        val stored = load(WATCH_SITE_MEDIA_KEY, null as String?)
        val loaded = runCatching {
            stored?.let { gson.fromJson<MutableMap<String, WatchSiteMedia>>(it, type) }
        }.getOrNull() ?: mutableMapOf()

        cache = loaded
        return loaded
    }

    private fun keyOf(siteKey: String, anilistId: Int) = "$siteKey:$anilistId"

    fun get(siteKey: String, anilistId: Int): WatchSiteMedia? = all()[keyOf(siteKey, anilistId)]

    fun forMedia(anilistId: Int): Map<String, WatchSiteMedia> = all().values
        .filter { it.anilistId == anilistId }
        .associateBy { it.siteKey }

    fun put(media: WatchSiteMedia) {
        val current = all()
        val existing = current[keyOf(media.siteKey, media.anilistId)]
        // a resolved result never overwrites what the user typed in
        if (existing?.manual == true && !media.manual) return

        current[keyOf(media.siteKey, media.anilistId)] = media
        persist()
    }

    fun remove(siteKey: String, anilistId: Int) {
        all().remove(keyOf(siteKey, anilistId))
        persist()
    }

    private fun persist() {
        save(WATCH_SITE_MEDIA_KEY, gson.toJson(all(), type))
    }
}
