package com.revolgenx.anilib.media.data.watch

import com.revolgenx.anilib.media.data.model.MediaEpisodeModel
import com.revolgenx.anilib.media.data.model.MediaWatchModel
import com.revolgenx.anilib.media.data.watch.site.WatchSiteMedia
import com.revolgenx.anilib.type.MediaFormat
import com.revolgenx.anilib.type.MediaSeason
import com.revolgenx.anilib.type.MediaType

object WatchVars {

    fun build(
        media: MediaWatchModel,
        episode: MediaEpisodeModel?,
        action: WatchAction,
        siteMedia: Map<String, WatchSiteMedia>
    ): Map<String, String?> {
        val vars = mutableMapOf<String, String?>()

        vars["media.id"] = media.mediaId.takeIf { it > 0 }?.toString()
        vars["media.idMal"] = media.idMal?.toString()
        vars["media.type"] = MediaType.values().getOrNull(media.type ?: -1)?.rawValue
        vars["media.format"] = MediaFormat.values().getOrNull(media.format ?: -1)?.rawValue
        vars["media.season"] = MediaSeason.values().getOrNull(media.season ?: -1)?.rawValue
        vars["media.year"] = media.seasonYear?.toString()
        vars["media.episodes"] = media.episodes?.toString()
        vars["media.chapters"] = media.chapters?.toString()

        vars["media.title.romaji"] = media.title?.romaji
        vars["media.title.english"] = media.title?.english
        vars["media.title.native"] = media.title?.native
        vars["media.title.userPreferred"] = media.title?.userPreferred
        vars["media.title"] = media.title?.title() ?: media.title?.romaji
        media.synonyms?.forEachIndexed { index, synonym -> vars["media.synonyms.$index"] = synonym }

        val number = episode?.number
        vars["episode"] = number?.toString()
        vars["episode.title"] = episode?.title

        siteMedia.forEach { (key, site) ->
            vars["site.$key"] = site.siteMediaId
            vars["site.$key.episode"] = number?.let { site.episodes[it]?.id }
        }

        action.vars.forEach { (key, value) -> vars["preset.$key"] = value }

        return vars
    }
}
