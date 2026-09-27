package com.revolgenx.anilib.list.viewmodel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.revolgenx.anilib.common.preference.*
import com.revolgenx.anilib.list.data.field.MediaListCollectionField
import com.revolgenx.anilib.list.data.meta.MediaListCollectionFilterMeta
import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.list.data.model.MediaListModel
import com.revolgenx.anilib.list.data.sorting.MediaListCollectionSortingComparator
import com.revolgenx.anilib.list.service.MediaListCollectionService
import com.revolgenx.anilib.type.MediaListStatus
import com.revolgenx.anilib.type.MediaStatus
import com.revolgenx.anilib.type.MediaType
import com.revolgenx.anilib.common.viewmodel.BaseViewModel
import com.revolgenx.anilib.entry.data.field.SaveMediaListEntryField
import com.revolgenx.anilib.entry.service.MediaListEntryService
import com.revolgenx.anilib.list.data.tag.ListTags
import com.revolgenx.anilib.entry.service.increaseProgress
import com.revolgenx.anilib.list.data.model.MediaListCollectionModel
import com.revolgenx.anilib.list.source.MediaListCollectionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.Exception

private const val ALL_GROUP = "All"

class MediaListCollectionVM(
    private val alMediaListCollectionService: MediaListCollectionService,
    private val mediaListEntryService: MediaListEntryService,
    private val mediaListCollectionStore: MediaListCollectionStoreVM
) : BaseViewModel() {

    val field: MediaListCollectionField = MediaListCollectionField()

    private val isLoggedInUser get() = field.userId == UserPreference.userId

    val mediaListFilter by lazy {
        if (isLoggedInUser) {
            loadMediaListCollectionFilter(type.ordinal)
        } else {
            MediaListCollectionFilterMeta()
        }.also {
            it.type = type.ordinal
        }
    }

    private val groupFilters: MutableMap<String, MediaListCollectionFilterMeta> by lazy {
        if (isLoggedInUser) loadMediaListGroupFilters(type.ordinal) else mutableMapOf()
    }

    val activeFilter: MediaListCollectionFilterMeta
        get() = groupFilters[currentGroupNameHistory] ?: mediaListFilter

    val hasGroupFilter get() = groupFilters.containsKey(currentGroupNameHistory)
    val groupsWithOwnFilter: Set<String> get() = groupFilters.keys

    val hiddenCount = MutableLiveData(0)

    fun getKnownTags(): List<String> {
        val used = mediaListCollectionModel?.lists
            ?.asSequence()
            ?.flatMap { it.entries.orEmpty().asSequence() }
            ?.flatMap { it.tags.asSequence() }
            ?.toSortedSet()
            .orEmpty()
        val custom = used - ListTags.defaults.toSet()
        return ListTags.defaults + custom
    }

    private val handler = Handler(Looper.getMainLooper())
    private val sortingComparator = MediaListCollectionSortingComparator()

    private var mediaListCollectionModel: MediaListCollectionModel? = null

    val groupNamesWithCount = MutableLiveData<Map<String, Int>>()

    val sourceLiveData = MutableLiveData<MediaListCollectionSource>()
    val collectionSource get() = sourceLiveData.value

    private val loadingSource = MediaListCollectionSource(Resource.loading(null))

    val currentGroupNameHistory
        get() = if (isLoggedInUser) {
            if (field.type == MediaType.ANIME)
                animeListStatusHistory()
            else
                mangaListStatusHistory()
        } else {
            groupNameHistory
        }

    var groupNameHistory = "All"

    var searchViewVisible = false

    var query = ""
    var search: String = ""
        set(value) {
            if (field != value) {
                query = value
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    filter()
                }, 500)
            }
            field = value
        }
        get() = query

    var type: MediaType
        set(value) {
            this.field.type = value
        }
        get() = this.field.type

    fun getMediaList() {
        sourceLiveData.value = loadingSource
        alMediaListCollectionService.getMediaListCollection(field, compositeDisposable) {
            when (it) {
                is Resource.Success -> {
                    mediaListCollectionModel = it.data ?: return@getMediaListCollection
                    if (isLoggedInUser) {
                        mediaListCollectionStore.lists =
                            mediaListCollectionModel?.lists ?: mutableListOf()
                    }
                    reEvaluateGroupNameWithCount()
                    filter()
                }
                is Resource.Error -> {
                    sourceLiveData.value = MediaListCollectionSource(Resource.error(it.message))
                }
                else -> {
                }
            }
        }
    }

    fun reEvaluateGroupNameWithCount() {
        val lists = mediaListCollectionModel?.lists ?: return
        val groupNameMap = mutableMapOf<String, Int>()
        groupNameMap["All"] = lists.first { it.name == "All" }.entries!!.count()
        mediaListCollectionModel!!.user!!.mediaListOptions!!.let {
            when (field.type) {
                MediaType.ANIME -> {
                    it.animeList?.sectionOrder?.forEach { order ->
                        lists.firstOrNull { it.name == order }?.let {
                            groupNameMap[order] = it.entries?.count() ?: 0
                        }
                    }
                }
                MediaType.MANGA -> {
                    it.mangaList?.sectionOrder?.forEach { order ->
                        lists.firstOrNull { it.name == order }?.let {
                            groupNameMap[order] = it.entries?.count() ?: 0
                        }
                    }
                }
                else -> {
                }
            }
        }
        groupNamesWithCount.value = groupNameMap
    }

    fun applyFilter(filter: MediaListCollectionFilterMeta, thisGroupOnly: Boolean) {
        val group = currentGroupNameHistory ?: ALL_GROUP
        if (thisGroupOnly && group != ALL_GROUP) {
            groupFilters[group] = filter.also { it.type = type.ordinal }
        } else {
            groupFilters.remove(group)
            mediaListFilter.copyFrom(filter)
        }
        persistFilters()
        filter()
    }

    fun clearGroupFilter() {
        groupFilters.remove(currentGroupNameHistory ?: ALL_GROUP)
        persistFilters()
        filter()
    }

    private fun persistFilters() {
        if (!isLoggedInUser) return
        storeMediaListFilterField(mediaListFilter)
        storeMediaListGroupFilters(type.ordinal, groupFilters)
    }

    fun filter() {
        sourceLiveData.value = loadingSource
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (isLoggedInUser) {
                        val hasRecentGroupNameHistory =
                            mediaListCollectionModel?.lists?.any { it.name == currentGroupNameHistory } == true
                        if (!hasRecentGroupNameHistory) {
                            if (field.type == MediaType.ANIME) {
                                animeListStatusHistory("All")
                            } else {
                                mangaListStatusHistory("All")
                            }
                        }
                    }


                    val mediaListEntries =
                        mediaListCollectionModel
                            ?.lists
                            ?.firstOrNull { it.name == currentGroupNameHistory }
                            ?.entries
                            ?: emptyList()

                    val filteredList = getFilteredList(mediaListEntries, activeFilter)
                    hiddenCount.postValue(mediaListEntries.size - filteredList.size)
                    sourceLiveData.postValue(MediaListCollectionSource(Resource.success(filteredList)))
                } catch (e: Exception) {
                    Timber.e(e)
                    launch(Dispatchers.Main) {
                        sourceLiveData.postValue(
                            MediaListCollectionSource(
                                Resource.error(
                                    e.message ?: "",
                                    null,
                                    e
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    fun setTags(item: MediaListModel, tags: List<String>) {
        val saveField = SaveMediaListEntryField().also {
            it.id = item.id
            it.notes = ListTags.compose(item.notes, tags)
        }
        item.onDataChanged?.invoke(Resource.loading(item))
        mediaListEntryService.saveMediaListEntry(saveField, compositeDisposable) { resource ->
            if (resource is Resource.Success) {
                item.notes = resource.data?.notes ?: saveField.notes
                if (!activeFilter.tags.isNullOrEmpty()) filter()
            }
            item.onDataChanged?.invoke(resource)
        }
    }

    fun increaseProgress(item: MediaListModel) {
        mediaListEntryService.increaseProgress(item, compositeDisposable)
    }

    fun onEntryEdited(edited: MediaListModel) {
        val lists = mediaListCollectionModel?.lists ?: return
        val success = Resource.success(edited)
        for (group in lists) {
            val entries = group.entries ?: continue
            for (entry in entries) {
                if (entry.id != edited.id) continue
                entry.updateFrom(edited)
                entry.onDataChanged?.invoke(success)
            }
        }
    }

    fun onEntryDeleted(entryId: Int) {
        val lists = mediaListCollectionModel?.lists ?: return
        for (group in lists) {
            group.entries?.removeAll { it.id == entryId }
        }
        reEvaluateGroupNameWithCount()
        filter()
    }

    private fun getFilteredList(
        listCollection: List<MediaListModel>,
        mediaListFilter: MediaListCollectionFilterMeta
    ): List<MediaListModel> {
        return if (mediaListFilter.formatsIn.isNullOrEmpty()) listCollection else {
            listCollection.filter { mediaListFilter.formatsIn!!.contains(it.media?.format) }
        }.let {
            if (mediaListFilter.status == null) it else it.filter { it.media?.status == mediaListFilter.status }
        }.let {
            if (mediaListFilter.genre == null) it else it.filter {
                it.media?.genres?.contains(
                    mediaListFilter.genre!!
                ) == true
            }
        }.let {
            if (mediaListFilter.isHentai == null) it else it.filter { it.media?.isAdult == mediaListFilter.isHentai }
        }.let {
            if (!mediaListFilter.hideNotYetReleased) it else it.filter {
                it.media?.status != MediaStatus.NOT_YET_RELEASED.ordinal
            }
        }.let {
            val tags = mediaListFilter.tags
            if (tags.isNullOrEmpty()) it else it.filter { entry -> entry.tags.containsAll(tags) }
        }.let {
            if (!mediaListFilter.hideWatchedSequels) it else it.filter { entry ->
                entry.status != MediaListStatus.PLANNING.ordinal || entry.media?.hasWatchedPrequel != true
            }
        }.let {
            if (query.isEmpty()) it else {
                it.filter { model ->
                    model.media?.title!!.romaji?.contains(query, true) == true ||
                            model.media?.title!!.english?.contains(
                                query,
                                true
                            ) == true ||
                            model.media?.title!!.native?.contains(
                                query,
                                true
                            ) == true ||
                            model.media?.synonyms?.any {
                                it.contains(
                                    query,
                                    true
                                )
                            } == true
                }
            }
        }.let {
            if (mediaListFilter.sort == null) it else {
                sortingComparator.type =
                    MediaListCollectionSortingComparator.MediaListSortingType.values()[mediaListFilter.sort!!]
                it.sortedWith(sortingComparator)
            }
        }.toMutableList()
    }
}