package com.revolgenx.anilib.media.data.watch

import java.util.UUID

enum class WatchActionScope { EPISODE, MEDIA, BOTH }

data class WatchActionParam(
    val key: String = "",
    val value: String = "",
    val omitIfBlank: Boolean = true
)

/** Sites are data, not code, a new site or filter is a new preset. */
data class WatchAction(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val pathTemplate: String? = null,
    val params: List<WatchActionParam> = emptyList(),
    val vars: Map<String, String> = emptyMap(),
    val scope: WatchActionScope = WatchActionScope.BOTH,
    /** MediaType ordinal, null applies to anime and manga. */
    val mediaType: Int? = null,
    /** Links the action to a site resolver and to an inline engine. */
    val siteKey: String? = null,
    val inlineResults: Boolean = false,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val order: Int = 0
) {
    val supportsInline get() = WatchInlineEngines.of(siteKey) != null

    fun appliesTo(episodeScoped: Boolean, mediaTypeOrdinal: Int?): Boolean {
        if (!enabled) return false
        if (mediaType != null && mediaTypeOrdinal != null && mediaType != mediaTypeOrdinal) return false
        return when (scope) {
            WatchActionScope.BOTH -> true
            WatchActionScope.EPISODE -> episodeScoped
            WatchActionScope.MEDIA -> !episodeScoped
        }
    }

    fun buildUrl(vars: Map<String, String?>): String = buildUrl(baseUrl, vars)

    fun buildInlineUrl(vars: Map<String, String?>): String? {
        val engine = WatchInlineEngines.of(siteKey) ?: return null
        return buildUrl(engine.apiUrl, vars)
    }

    private fun buildUrl(base: String, vars: Map<String, String?>): String {
        val builder = StringBuilder(WatchTemplate.render(base, vars, WatchEncode.PATH))

        pathTemplate?.takeIf { it.isNotBlank() }?.let { path ->
            val rendered = WatchTemplate.render(path, vars, WatchEncode.PATH)
            if (rendered.isNotBlank()) {
                if (!builder.endsWith("/") && !rendered.startsWith("/")) builder.append('/')
                builder.append(rendered)
            }
        }

        var first = !builder.contains("?")
        params.forEach { param ->
            if (param.key.isBlank()) return@forEach
            val value = WatchTemplate.render(param.value, vars, WatchEncode.QUERY)
            if (value.isBlank() && param.omitIfBlank) return@forEach

            builder.append(if (first) '?' else '&').append(param.key).append('=').append(value)
            first = false
        }

        return builder.toString()
    }
}
