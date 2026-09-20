package com.revolgenx.anilib.media.data.model

import com.revolgenx.anilib.type.MediaType

class MediaWatchModel {
    var mediaId: Int = -1
    var idMal: Int? = null
    var type: Int? = null
    var format: Int? = null
    var season: Int? = null
    var seasonYear: Int? = null
    var episodes: Int? = null
    var chapters: Int? = null
    var title: MediaTitleModel? = null
    var synonyms: List<String>? = null
    var nextAiringEpisode: Int? = null
    var nextAiringAt: Long? = null
    var progress: Int = 0
    var streamingEpisodes: List<MediaStreamingEpisodeModel> = emptyList()

    val isAnime get() = type == MediaType.ANIME.ordinal
    val unitCount get() = if (isAnime) episodes else chapters
}
