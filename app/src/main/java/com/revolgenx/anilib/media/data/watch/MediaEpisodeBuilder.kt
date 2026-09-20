package com.revolgenx.anilib.media.data.watch

import com.revolgenx.anilib.media.data.model.MediaEpisodeModel
import com.revolgenx.anilib.media.data.model.MediaStreamingEpisodeModel
import com.revolgenx.anilib.media.data.model.MediaWatchModel
import com.revolgenx.anilib.media.data.watch.site.WatchSiteEpisode

data class WatchEpisodeFilter(
    val descending: Boolean = false,
    val unwatchedOnly: Boolean = false,
    val showBatch: Boolean = true
)

/**
 * Episodes are synthesized, AniList only has streamingEpisodes for a fraction of media and they
 * carry no episode number, so every other source is merged in by number.
 */
object MediaEpisodeBuilder {

    private val numberRegexes = listOf(
        Regex("""(?:^|\s)(?:episode|ep|e)\s*\.?\s*(\d{1,4})""", RegexOption.IGNORE_CASE),
        Regex("""第\s*(\d{1,4})\s*話"""),
        Regex("""(?:^|\s)#(\d{1,4})""")
    )

    fun build(
        model: MediaWatchModel,
        filter: WatchEpisodeFilter,
        siteEpisodes: Map<Int, WatchSiteEpisode> = emptyMap()
    ): List<MediaEpisodeModel> {
        val byNumber = mutableMapOf<Int, MediaStreamingEpisodeModel>()
        val unnumbered = mutableListOf<MediaStreamingEpisodeModel>()

        model.streamingEpisodes.forEach { episode ->
            val number = parseNumber(episode.title)
            if (number != null && !byNumber.containsKey(number)) byNumber[number] = episode
            else unnumbered += episode
        }

        val count = maxOf(
            model.unitCount ?: 0,
            model.nextAiringEpisode?.minus(1) ?: 0,
            byNumber.keys.maxOrNull() ?: 0,
            siteEpisodes.keys.maxOrNull() ?: 0,
            if (byNumber.isEmpty()) model.streamingEpisodes.size else 0
        )

        val now = System.currentTimeMillis() / 1000
        val episodes = (1..count).map { number ->
            val stream = byNumber[number]
            val site = siteEpisodes[number]
            val airingAt = when {
                number == model.nextAiringEpisode -> model.nextAiringAt
                else -> site?.airingAt
            }
            val aired = when {
                model.nextAiringEpisode != null -> number < model.nextAiringEpisode!!
                airingAt != null -> airingAt <= now
                else -> true
            }

            MediaEpisodeModel(
                number = number,
                title = stream?.title?.let { stripNumber(it) } ?: site?.title,
                thumbnail = stream?.thumbnail,
                streamingUrl = stream?.url,
                streamingSite = stream?.site,
                airingAt = airingAt,
                watched = number <= model.progress,
                nextToWatch = number == model.progress + 1 && aired,
                aired = aired,
                nextAiring = number == model.nextAiringEpisode
            )
        }.toMutableList()

        unnumbered.forEach { stream ->
            episodes += MediaEpisodeModel(
                number = null,
                title = stream.title,
                thumbnail = stream.thumbnail,
                streamingUrl = stream.url,
                streamingSite = stream.site
            )
        }

        val filtered = if (filter.unwatchedOnly) episodes.filter { !it.watched } else episodes
        val ordered = if (filter.descending) filtered.reversed() else filtered

        if (!filter.showBatch) return ordered

        // nothing watched yet, a batch is the likely pick, so it gets the bigger tile
        val batch = MediaEpisodeModel(
            number = null,
            batch = true,
            emphasized = model.progress == 0
        )

        return listOf(batch) + ordered
    }

    /** Index of the tile the list should open on, the next unwatched aired episode. */
    fun nextToWatchIndex(episodes: List<MediaEpisodeModel>): Int =
        episodes.indexOfFirst { it.nextToWatch }

    private fun parseNumber(title: String?): Int? {
        val text = title ?: return null
        numberRegexes.forEach { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun stripNumber(title: String): String {
        val trimmed = title.substringAfter(" - ", title).trim()
        return trimmed.ifEmpty { title }
    }
}
