package com.revolgenx.anilib.media.viewmodel

import androidx.lifecycle.MutableLiveData
import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.common.viewmodel.BaseViewModel
import com.revolgenx.anilib.media.data.crawler.FranchiseCrawlLimits
import com.revolgenx.anilib.media.data.field.MediaFranchiseField
import com.revolgenx.anilib.media.data.model.FranchiseGraph
import com.revolgenx.anilib.media.data.order.FranchiseFilter
import com.revolgenx.anilib.media.data.order.FranchiseOrderBuilder
import com.revolgenx.anilib.media.data.order.FranchiseOrderMode
import com.revolgenx.anilib.media.data.order.FranchiseSummary
import com.revolgenx.anilib.media.service.MediaInfoService
import com.revolgenx.anilib.media.source.MediaChronologySource

class MediaChronologyVM(private val mediaInfoService: MediaInfoService) : BaseViewModel() {

    val field = MediaFranchiseField()

    var orderMode = FranchiseOrderMode.RELEASE
    var filter = FranchiseFilter()

    private var graph: FranchiseGraph? = null
    private var loaded = false
    private var loading = false

    val sourceLiveData = MutableLiveData<MediaChronologySource>()
    val summaryLiveData = MutableLiveData<FranchiseSummary?>()

    fun load(force: Boolean = false) {
        if (loading) return
        if (loaded && !force) return

        loaded = true
        loading = true
        graph = null
        field.limits = FranchiseCrawlLimits()

        sourceLiveData.value = MediaChronologySource(Resource.loading(null))
        summaryLiveData.value = null

        fetch(null)
    }

    fun loadMore() {
        if (loading) return
        val current = graph ?: return
        if (!current.canLoadMore) return

        loading = true
        // depth has to grow too, a crawl stopped by maxDepth would otherwise resume into nothing
        field.limits = field.limits.copy(
            maxMedia = current.nodes.size + FranchiseCrawlLimits.DEFAULT_MAX_MEDIA,
            maxDepth = field.limits.maxDepth + FranchiseCrawlLimits.DEPTH_STEP
        )
        fetch(current)
    }

    /** Chip toggles land here, re-sorting what is already in memory instead of refetching. */
    fun rebuild() {
        val current = graph ?: return
        val rows = FranchiseOrderBuilder.build(current, orderMode, filter)
        summaryLiveData.value = FranchiseOrderBuilder.summarize(rows, current)
        sourceLiveData.value = MediaChronologySource(Resource.success(rows))
    }

    private fun fetch(resumeFrom: FranchiseGraph?) {
        mediaInfoService.getMediaFranchise(field, resumeFrom, compositeDisposable) { resource ->
            loading = false
            when (resource) {
                is Resource.Success -> {
                    graph = resource.data
                    rebuild()
                }
                is Resource.Error -> {
                    if (resumeFrom == null) {
                        loaded = false
                        sourceLiveData.value = MediaChronologySource(Resource.error(resource.message))
                    }
                }
                else -> {}
            }
        }
    }
}
