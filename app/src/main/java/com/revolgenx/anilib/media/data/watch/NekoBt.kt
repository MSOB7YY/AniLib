package com.revolgenx.anilib.media.data.watch

/** Everything nekoBT specific lives here as data, the search page and the json api share it. */
object NekoBt {
    const val SITE_KEY = "nekobt"
    const val SEARCH_URL = "https://nekobt.to/search"
    const val API_URL = "https://nekobt.to/api/v1/torrents/search"
    const val TORRENT_URL = "https://nekobt.to/torrents/"

    fun torrentFileUrl(torrentId: String) =
        "https://nekobt.to/api/v1/torrents/$torrentId/download?public=true"

    private fun options(vararg pairs: Pair<String, String>) =
        pairs.map { WatchFilterOption(it.first, it.second) }

    val sortOptions = options(
        "best" to "Best",
        "latest" to "Latest",
        "oldest" to "Oldest",
        "rss" to "Recently added",
        "seeders" to "Seeders",
        "seeders_asc" to "Seeders (asc)",
        "leechers" to "Leechers",
        "leechers_asc" to "Leechers (asc)",
        "downloads" to "Downloads",
        "downloads_asc" to "Downloads (asc)",
        "comments" to "Comments",
        "comments_asc" to "Comments (asc)",
        "filesize" to "File size",
        "filesize_asc" to "File size (asc)"
    )

    private val levelOptions = options(
        "0" to "Official",
        "1" to "Slight modifications",
        "2" to "Small-scale fansubs",
        "3" to "Full-scale fansubs"
    )

    val codecLabels = mapOf(
        1 to "H.264", 2 to "H.265", 3 to "AV1", 4 to "VP9",
        5 to "MPEG-2", 6 to "XviD", 7 to "WMV", 8 to "VC-1", 0 to "Other"
    )

    val videoTypeLabels = mapOf(
        15 to "Hybrid", 14 to "BD Remux", 13 to "BD Encode", 12 to "BD Mini", 11 to "BD Disc",
        9 to "WEB-DL", 8 to "WEB Encode", 7 to "WEB Mini", 6 to "DVD Encode", 5 to "DVD Remux",
        16 to "DVD Disc", 4 to "TV Raw", 3 to "TV Encode", 2 to "LaserDisc", 1 to "VHS", 0 to "Other"
    )

    private val codecOptions = codecLabels.map { WatchFilterOption(it.key.toString(), it.value) }
    private val videoTypeOptions =
        videoTypeLabels.map { WatchFilterOption(it.key.toString(), it.value) }

    val languageOptions = options(
        "ja" to "Japanese", "en" to "English", "es-es" to "Spanish", "es-419" to "Spanish (LA)",
        "pt-pt" to "Portuguese", "pt-br" to "Portuguese (BR)", "fr-fr" to "French",
        "fr-ca" to "French (CA)", "de" to "German", "it" to "Italian", "ru" to "Russian",
        "pl" to "Polish", "ar" to "Arabic", "tr" to "Turkish", "zh" to "Chinese",
        "zh-hans" to "Chinese (simplified)", "zh-hant" to "Chinese (traditional)",
        "ko" to "Korean", "id" to "Indonesian", "th" to "Thai", "vi" to "Vietnamese",
        "hi" to "Hindi", "fil" to "Filipino", "nl" to "Dutch", "sv" to "Swedish",
        "no" to "Norwegian", "da" to "Danish", "fi" to "Finnish", "cs" to "Czech",
        "hu" to "Hungarian", "ro" to "Romanian", "uk" to "Ukrainian", "el" to "Greek",
        "he" to "Hebrew", "fa" to "Persian", "ms" to "Malay", "zxx" to "None"
    )

    val filters = listOf(
        WatchFilterSpec("sort_by", "Sort by", WatchFilterType.ENUM, sortOptions),
        WatchFilterSpec(
            "category", "Category", WatchFilterType.ENUM,
            options("0" to "All", "1" to "Anime")
        ),
        WatchFilterSpec("levels", "Subtitle levels", WatchFilterType.MULTI_ENUM, levelOptions),
        WatchFilterSpec("video_codec", "Video codec", WatchFilterType.MULTI_ENUM, codecOptions),
        WatchFilterSpec("video_type", "Video type", WatchFilterType.MULTI_ENUM, videoTypeOptions),
        WatchFilterSpec("audio_lang", "Audio languages", WatchFilterType.MULTI_ENUM, languageOptions),
        WatchFilterSpec("sub_lang", "Official subtitles", WatchFilterType.MULTI_ENUM, languageOptions),
        WatchFilterSpec("fansub_lang", "Fansub languages", WatchFilterType.MULTI_ENUM, languageOptions),
        WatchFilterSpec("batch", "Batches", WatchFilterType.TRISTATE),
        WatchFilterSpec("upgraded", "Upgraded", WatchFilterType.TRISTATE),
        WatchFilterSpec("hardsub", "Hardsub", WatchFilterType.TRISTATE),
        WatchFilterSpec("otl", "Official translation", WatchFilterType.TRISTATE),
        WatchFilterSpec("mtl", "Machine translation", WatchFilterType.TRISTATE),
        WatchFilterSpec("episode_match_any", "Match any episode", WatchFilterType.BOOL),
        WatchFilterSpec("group_id", "Group id", WatchFilterType.TEXT),
        WatchFilterSpec("group_primary", "Include group uploads", WatchFilterType.BOOL),
        WatchFilterSpec("group_secondary", "Include group tags", WatchFilterType.BOOL),
        WatchFilterSpec("group_childs", "Include child groups", WatchFilterType.BOOL),
        WatchFilterSpec("group_parents", "Include parent groups", WatchFilterType.BOOL),
        WatchFilterSpec("uploader_id", "Uploader id", WatchFilterType.TEXT),
        WatchFilterSpec("uploader_uploads", "Include uploads", WatchFilterType.BOOL),
        WatchFilterSpec("uploader_contributions", "Include contributions", WatchFilterType.BOOL),
        WatchFilterSpec("limit", "Result limit", WatchFilterType.TEXT, hint = "50")
    )
}
