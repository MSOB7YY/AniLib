package com.revolgenx.anilib.social.markwon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.util.Linkify
import android.view.View
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin

/**
 * Builds the [Markwon] instance used everywhere the app renders user written text.
 *
 * Text is expected to have been passed through [AlStringUtil.anilify] first. The AniList
 * specific constructs then arrive as ordinary markdown links with a private scheme, and
 * [AlLinkResolver] turns taps on them back into [AlMarkwonCallback] calls.
 */
object AlMarkwon {

    @Volatile
    private var cached: Markwon? = null

    @Volatile
    private var cachedCallback: AlMarkwonCallback? = null

    /**
     * Returns a [Markwon] configured for AniList content. The instance is reused for as long as
     * [callback] stays the same, since building one is not cheap and callers ask per rendered view.
     */
    fun getMarkwon(context: Context, callback: AlMarkwonCallback): Markwon {
        cached?.let { if (cachedCallback === callback) return it }

        val resolver = AlLinkResolver(context.applicationContext, callback)
        val markwon = Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            // AniList descriptions arrive with raw html mixed into the markdown
            .usePlugin(HtmlPlugin.create())
            // bare urls should be tappable too, and the plugin routes them through our resolver
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
            .usePlugin(MovementMethodPlugin.link())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver(resolver)
                }

                /**
                 * Every screen that renders markdown goes through here, so AniList syntax works
                 * even where the caller never touched [AlStringUtil.anilify] itself, which is the
                 * case for character, staff and media descriptions. anilify is idempotent, so
                 * callers that do pre-anilify their text are unaffected.
                 */
                override fun processMarkdown(markdown: String): String =
                    AlStringUtil.anilify(markdown)
            })
            .build()

        // the resolver renders spoiler bodies with the very instance it belongs to
        resolver.markwon = markwon

        cached = markwon
        cachedCallback = callback
        return markwon
    }
}

private class AlLinkResolver(
    private val context: Context,
    private val callback: AlMarkwonCallback
) : LinkResolver {

    @Volatile
    var markwon: Markwon? = null

    override fun resolve(view: View, link: String) {
        when {
            link.startsWith(AlStringUtil.SCHEME_SPOILER) -> {
                val markdown = AlStringUtil.decode(link.removePrefix(AlStringUtil.SCHEME_SPOILER))
                callback.onSpoilerClick(
                    markwon?.toMarkdown(markdown) ?: SpannableString(markdown)
                )
            }
            link.startsWith(AlStringUtil.SCHEME_IMAGE) ->
                callback.onImageClick(AlStringUtil.decode(link.removePrefix(AlStringUtil.SCHEME_IMAGE)))

            link.startsWith(AlStringUtil.SCHEME_VIDEO) ->
                callback.onVideoClick(AlStringUtil.decode(link.removePrefix(AlStringUtil.SCHEME_VIDEO)))

            link.startsWith(AlStringUtil.SCHEME_YOUTUBE) ->
                callback.onYoutubeClick(link.removePrefix(AlStringUtil.SCHEME_YOUTUBE))

            link.startsWith(AlStringUtil.SCHEME_USER) ->
                callback.onUserMentionClicked(link.removePrefix(AlStringUtil.SCHEME_USER))

            else -> resolveWebLink(link)
        }
    }

    /**
     * Keeps anilist.co and youtube urls inside the app, and hands anything else to the system.
     */
    private fun resolveWebLink(link: String) {
        val uri = runCatching { Uri.parse(link) }.getOrNull()
        val host = uri?.host?.removePrefix("www.")?.removePrefix("m.")
        val segments = uri?.pathSegments.orEmpty()

        if (host == "anilist.co" && segments.size >= 2) {
            val type = segments[0].lowercase()
            val slug = segments[1]

            if (type == "user") {
                callback.onUserMentionClicked(slug)
                return
            }

            val id = slug.toIntOrNull()
            if (id != null && (type == "anime" || type == "manga" || type == "character")) {
                callback.onAniListLinkClick(id, type)
                return
            }
        }

        if (host == "youtube.com" || host == "youtu.be") {
            val id = AlStringUtil.youtubeId(link)
            if (id.isNotEmpty() && id != link) {
                callback.onYoutubeClick(id)
                return
            }
        }

        openExternally(link)
    }

    private fun openExternally(link: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
