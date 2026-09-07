package com.revolgenx.anilib.list.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.revolgenx.anilib.type.MediaType

class MediaListContainerSharedVM : ViewModel() {
    var userId: Int? = null
    var userName: String? = null

    val hasUserData get()= (userId ?: userName) != null

    var currentGroupNameWithCount = MutableLiveData<Pair<String, Int>?>()
    var mediaListContainerCallback = MutableLiveData<Pair<MediaListCollectionContainerCallback, Int>>()

    private val animeGroupState = MutableLiveData<MediaListGroupState?>()
    private val mangaGroupState = MutableLiveData<MediaListGroupState?>()

    /** Group picked from the container, as name to [MediaType] ordinal. */
    val groupSelection = MutableLiveData<Pair<String, Int>?>()

    var animeListScroller: MediaListScroller? = null
    var mangaListScroller: MediaListScroller? = null

    fun groupState(mediaType: MediaType) =
        if (mediaType == MediaType.ANIME) animeGroupState else mangaGroupState
}

/** The groups a list page can be filtered by, in the order the user ordered them. */
data class MediaListGroupState(
    val groups: List<Pair<String, Int>>,
    val selected: String?
)

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
