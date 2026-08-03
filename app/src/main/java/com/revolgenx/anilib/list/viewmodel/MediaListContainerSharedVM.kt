package com.revolgenx.anilib.list.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MediaListContainerSharedVM : ViewModel() {
    var userId: Int? = null
    var userName: String? = null

    val hasUserData get()= (userId ?: userName) != null

    var currentGroupNameWithCount = MutableLiveData<Pair<String, Int>?>()
    var mediaListContainerCallback = MutableLiveData<Pair<MediaListCollectionContainerCallback, Int>>()

    var animeListScroller: MediaListScroller? = null
    var mangaListScroller: MediaListScroller? = null
}

/**
 * Lets the container scroll a list it does not own. Each list lives in a pager child fragment, so
 * the container has no handle on the recycler view itself.
 */
interface MediaListScroller {
    fun isAtTop(): Boolean
    fun scrollToTop()
    fun scrollToBottom()
}

enum class MediaListCollectionContainerCallback{
    SEARCH, GROUP, CURRENT_TAB, FILTER, DISPLAY
}