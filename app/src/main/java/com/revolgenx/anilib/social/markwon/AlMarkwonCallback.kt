package com.revolgenx.anilib.social.markwon

import android.text.Spanned

/**
 * Receives the interactions that AniList flavored markdown can produce.
 *
 * Every construct that [AlStringUtil.anilify] rewrites ends up here once the user taps it,
 * so implementors decide how the app reacts (open a viewer, post an event, follow a link).
 */
interface AlMarkwonCallback {

    /** [link] is the youtube video id, not the full url. */
    fun onYoutubeClick(link: String)

    /** [link] is the full url of a video, e.g. the target of a `webm(...)` tag. */
    fun onVideoClick(link: String)

    /** [link] is the full url of an image, e.g. the target of an `img(...)` tag. */
    fun onImageClick(link: String)

    /** [spanned] is the already rendered content that was hidden behind `~!...!~`. */
    fun onSpoilerClick(spanned: Spanned)

    fun onUserMentionClicked(username: String)

    /** [type] is one of `anime`, `manga` or `character`. */
    fun onAniListLinkClick(id: Int, type: String)
}
