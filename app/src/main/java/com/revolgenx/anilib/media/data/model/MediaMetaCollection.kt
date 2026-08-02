package com.revolgenx.anilib.media.data.model

import android.text.SpannableStringBuilder

class MediaMetaCollection() {
    lateinit var header: String
    var subTitle: String? = null
    var subTitleSpannable: SpannableStringBuilder? = null

    /** Keeps the value hidden until tapped, see the hide global rating preference. */
    var isSpoiler = false

    /** Lives on the model rather than the view so a reveal survives recycling. */
    var isRevealed = false
}