package com.revolgenx.anilib.media.data.watch

data class WatchTorrentModel(
    val id: String,
    val title: String,
    val magnet: String? = null,
    val webUrl: String? = null,
    val torrentUrl: String? = null,
    val size: Long = 0,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val completed: Int = 0,
    val uploadedAt: Long = 0,
    val batch: Boolean = false,
    val group: String? = null,
    val quality: String? = null,
    val audioLanguages: List<String> = emptyList(),
    val subLanguages: List<String> = emptyList(),
    val hardsub: Boolean = false,
    val preferred: Boolean = false
)

enum class WatchResultSort { SEEDERS, DATE, SIZE, TITLE }
