package com.revolgenx.anilib.media.data.watch.site

data class WatchSiteEpisode(
    val id: String,
    val number: Int,
    val title: String? = null,
    val airingAt: Long? = null
)

/** What a site knows about one AniList media, its own id and its own episode ids. */
data class WatchSiteMedia(
    val siteKey: String,
    val anilistId: Int,
    val siteMediaId: String,
    val episodes: Map<Int, WatchSiteEpisode> = emptyMap(),
    val manual: Boolean = false
)
