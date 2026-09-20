package com.revolgenx.anilib.media.data.watch

enum class WatchFilterType { TEXT, ENUM, MULTI_ENUM, TRISTATE, BOOL }

data class WatchFilterOption(val value: String, val label: String)

/** Describes one site filter so the editor can render it without knowing the site. */
data class WatchFilterSpec(
    val key: String,
    val label: String,
    val type: WatchFilterType,
    val options: List<WatchFilterOption> = emptyList(),
    val hint: String? = null
)

object WatchSiteSpecs {
    fun of(siteKey: String?): List<WatchFilterSpec> = when (siteKey) {
        NekoBt.SITE_KEY -> NekoBt.filters
        else -> emptyList()
    }
}
