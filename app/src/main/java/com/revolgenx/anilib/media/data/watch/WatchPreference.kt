package com.revolgenx.anilib.media.data.watch

import com.revolgenx.anilib.common.preference.load
import com.revolgenx.anilib.common.preference.save

/** Watch tab preferences, global, they never depend on the media being viewed. */
object WatchPreference {

    private const val WATCH_PREFERRED_GROUPS_KEY = "WATCH_PREFERRED_GROUPS_KEY"
    private const val WATCH_RESULT_LANGUAGES_KEY = "WATCH_RESULT_LANGUAGES_KEY"
    private const val WATCH_DESCENDING_KEY = "WATCH_DESCENDING_KEY"
    private const val WATCH_UNWATCHED_ONLY_KEY = "WATCH_UNWATCHED_ONLY_KEY"

    /** The episode chips are global, the same order is expected on the next media. */
    var episodeFilter: WatchEpisodeFilter
        get() = filterCache ?: WatchEpisodeFilter(
            descending = load(WATCH_DESCENDING_KEY, false),
            unwatchedOnly = load(WATCH_UNWATCHED_ONLY_KEY, false)
        ).also { filterCache = it }
        set(value) {
            filterCache = value
            save(WATCH_DESCENDING_KEY, value.descending)
            save(WATCH_UNWATCHED_ONLY_KEY, value.unwatchedOnly)
        }

    private var filterCache: WatchEpisodeFilter? = null
    private var groupCache: Set<String>? = null
    private var languageCache: Set<String>? = null

    var preferredGroupsRaw: String
        get() = load(WATCH_PREFERRED_GROUPS_KEY, "").orEmpty()
        set(value) {
            save(WATCH_PREFERRED_GROUPS_KEY, value)
            groupCache = null
        }

    var resultLanguagesRaw: String
        get() = load(WATCH_RESULT_LANGUAGES_KEY, "").orEmpty()
        set(value) {
            save(WATCH_RESULT_LANGUAGES_KEY, value)
            languageCache = null
        }

    /** Lowercased, a result is matched against these on every row. */
    val preferredGroups: Set<String>
        get() = groupCache ?: parse(preferredGroupsRaw).also { groupCache = it }

    val resultLanguages: Set<String>
        get() = languageCache ?: parse(resultLanguagesRaw).also { languageCache = it }

    private fun parse(raw: String): Set<String> = raw.split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()
}
