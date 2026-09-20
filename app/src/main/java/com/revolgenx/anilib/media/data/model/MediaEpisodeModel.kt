package com.revolgenx.anilib.media.data.model

/** [number] is null for the tile that targets the whole media, a batch release for example. */
data class MediaEpisodeModel(
    val number: Int?,
    val title: String? = null,
    val thumbnail: String? = null,
    val streamingUrl: String? = null,
    val streamingSite: String? = null,
    val airingAt: Long? = null,
    val watched: Boolean = false,
    val nextToWatch: Boolean = false,
    val aired: Boolean = true,
    val nextAiring: Boolean = false,
    val batch: Boolean = false,
    val emphasized: Boolean = false
)
