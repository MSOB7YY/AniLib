package com.revolgenx.anilib.media.data.model

import com.revolgenx.anilib.common.data.model.BaseImageModel
import com.revolgenx.anilib.common.preference.imageQuality
import com.revolgenx.anilib.common.preference.malCoversEnabled
import com.revolgenx.anilib.media.data.cover.MalCovers

class MediaCoverImageModel(
    medium: String?,
    large: String?,
    val extraLarge: String?,
    val malKey: Int = 0
) : BaseImageModel(medium, large) {
    var color: String? = null

    private var malPath = 0L
    private var malImage: String? = null
    private var malLargeImage: String? = null

    val sImage: String
        get() = medium ?: large ?: ""

    fun largeImage(): String = image(true)

    fun image(): String = image(false)

    fun image(large: Boolean): String = malImage(large) ?: aniListImage(large)

    fun aniListImage(large: Boolean): String {
        if (large) return extraLarge ?: image
        return when (imageQuality()) {
            "0" -> image
            "1" -> sImage
            else -> extraLarge ?: image
        }
    }

    fun malImage(large: Boolean): String? {
        if (malKey == 0 || !malCoversEnabled()) return null
        val path = MalCovers.path(malKey)
        if (path == 0L) return null

        if (path != malPath) {
            malPath = path
            malImage = null
            malLargeImage = null
        }
        val isLarge = large || isLargeQuality()
        return if (isLarge) {
            malLargeImage ?: MalCovers.url(malKey, path, true).also { malLargeImage = it }
        } else {
            malImage ?: MalCovers.url(malKey, path, false).also { malImage = it }
        }
    }

    private fun isLargeQuality(): Boolean {
        val quality = imageQuality()
        return quality != "0" && quality != "1"
    }

}
