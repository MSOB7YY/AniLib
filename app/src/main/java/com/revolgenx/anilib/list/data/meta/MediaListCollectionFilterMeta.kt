package com.revolgenx.anilib.list.data.meta

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaListCollectionFilterMeta(
    var formatsIn: MutableList<Int>? = null,
    var status: Int? = null,
    var genre: String? = null,
    var sort: Int? = null,
    var type: Int? = null,
    var isHentai: Boolean? = null,
    var hideNotYetReleased: Boolean = false,
    var hideWatchedSequels: Boolean = false,
    var tags: MutableList<String>? = null
) : Parcelable {

    val hidesAnything
        get() = !formatsIn.isNullOrEmpty() || status != null || genre != null || isHentai != null ||
                hideNotYetReleased || hideWatchedSequels || !tags.isNullOrEmpty()

    fun copyFrom(other: MediaListCollectionFilterMeta) {
        formatsIn = other.formatsIn
        status = other.status
        genre = other.genre
        sort = other.sort
        isHentai = other.isHentai
        hideNotYetReleased = other.hideNotYetReleased
        hideWatchedSequels = other.hideWatchedSequels
        tags = other.tags
    }
}
