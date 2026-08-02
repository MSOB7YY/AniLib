package com.revolgenx.anilib.social.markwon

import android.util.Base64

/**
 * Rewrites AniList flavored markdown into plain CommonMark that [AlMarkwon] can render.
 *
 * AniList accepts a handful of constructs CommonMark knows nothing about: `img(url)`,
 * `youtube(url)`, `webm(url)`, `~!spoilers!~`, `~~~centered~~~` and `@mentions`. Each one is
 * rewritten into an ordinary markdown link carrying a private uri scheme. Rendering therefore
 * stays pure CommonMark, and every interaction arrives at a single link resolver instead of
 * needing a custom node, visitor and span per construct.
 *
 * Payloads that may contain characters a markdown link destination cannot hold (spaces,
 * parentheses, nested markdown) are base64 encoded with [encode].
 */
object AlStringUtil {

    internal const val SCHEME_IMAGE = "alimage://"
    internal const val SCHEME_YOUTUBE = "alyoutube://"
    internal const val SCHEME_VIDEO = "alvideo://"
    internal const val SCHEME_SPOILER = "alspoiler://"
    internal const val SCHEME_USER = "aluser://"

    // url safe alphabet keeps the payload legal inside a markdown link destination
    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    private val spoilerRegex = Regex("""~!([\s\S]*?)!~""")

    // img(url), img500(url) and img50%(url) all just mean "show this image"
    private val imageRegex = Regex("""\bimg\d*%?\(\s*([^)\s]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val youtubeRegex = Regex("""\byoutube\(\s*([^)\s]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val videoRegex = Regex("""\bwebm\(\s*([^)\s]+)\s*\)""", RegexOption.IGNORE_CASE)

    private val centerRegex = Regex("""~~~([\s\S]*?)~~~""")

    // a mention never follows a word character, an @ or a url separator, which keeps this from
    // firing inside email addresses and links. "[" is excluded too so that re-running anilify
    // over its own output does not wrap an already generated [@name](aluser://name) again.
    private val mentionRegex = Regex("""(?<![\w@/.\[])@([A-Za-z0-9_]{2,20})""")

    /**
     * Shown in place of hidden content. Not a string resource because [anilify] is called from
     * places that have no Context; the reveal sheet itself is localised.
     */
    private const val SPOILER_LABEL = "Click to show spoiler"

    private val youtubeIdRegex =
        Regex("""(?:v=|/v/|youtu\.be/|/embed/|/shorts/)([A-Za-z0-9_-]{6,})""")

    /**
     * Returns [text] with every AniList specific construct replaced by a markdown link.
     * Safe to call on text that contains none of them, in which case it is returned unchanged.
     */
    fun anilify(text: String): String {
        if (text.isEmpty()) return text

        // spoilers first: their content is itself AniList markdown, and encoding it here stops
        // the later passes from rewriting what is meant to stay hidden
        var result = spoilerRegex.replace(text) { match ->
            link(SPOILER_LABEL, SCHEME_SPOILER + encode(anilify(match.groupValues[1].trim())))
        }
        result = imageRegex.replace(result) { match ->
            link("image", SCHEME_IMAGE + encode(match.groupValues[1]))
        }
        result = youtubeRegex.replace(result) { match ->
            link("youtube video", SCHEME_YOUTUBE + youtubeId(match.groupValues[1]))
        }
        result = videoRegex.replace(result) { match ->
            link("video", SCHEME_VIDEO + encode(match.groupValues[1]))
        }
        // nothing to center in a TextView span, so keep the text and drop the markers
        result = centerRegex.replace(result) { match -> match.groupValues[1] }
        result = mentionRegex.replace(result) { match ->
            val username = match.groupValues[1]
            link("@$username", SCHEME_USER + username)
        }
        return result
    }

    /** Pulls the video id out of any of the youtube url shapes, falling back to [url] itself. */
    internal fun youtubeId(url: String): String =
        youtubeIdRegex.find(url)?.groupValues?.get(1) ?: url.substringAfterLast('/')

    internal fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), BASE64_FLAGS)

    internal fun decode(value: String): String = runCatching {
        String(Base64.decode(value, BASE64_FLAGS), Charsets.UTF_8)
    }.getOrDefault(value)

    private fun link(label: String, destination: String) = "[$label]($destination)"
}
