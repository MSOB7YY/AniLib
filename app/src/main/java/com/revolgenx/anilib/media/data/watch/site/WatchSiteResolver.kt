package com.revolgenx.anilib.media.data.watch.site

import com.revolgenx.anilib.media.data.model.MediaWatchModel
import okhttp3.OkHttpClient

/** Resolves a site's own media id from an AniList media, blocking, always called off the main thread. */
interface WatchSiteResolver {
    val siteKey: String

    /** Query parameter that carries the site id, used to read an id out of a pasted url. */
    val idParam: String

    fun resolve(client: OkHttpClient, media: MediaWatchModel): WatchSiteMedia?

    /** Episode ids for an id the user typed in, the site was never searched in that case. */
    fun episodes(client: OkHttpClient, siteMediaId: String): Map<Int, WatchSiteEpisode>
}

object WatchSiteResolvers {
    private val resolvers = listOf<WatchSiteResolver>(NekoBtSiteResolver)

    fun of(siteKey: String?): WatchSiteResolver? =
        siteKey?.let { key -> resolvers.firstOrNull { it.siteKey == key } }
}
