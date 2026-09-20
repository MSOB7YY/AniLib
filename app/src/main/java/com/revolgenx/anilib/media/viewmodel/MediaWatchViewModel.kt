package com.revolgenx.anilib.media.viewmodel

import androidx.lifecycle.MutableLiveData
import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.common.viewmodel.BaseViewModel
import com.revolgenx.anilib.media.data.field.MediaWatchField
import com.revolgenx.anilib.media.data.model.MediaEpisodeModel
import com.revolgenx.anilib.media.data.model.MediaWatchModel
import com.revolgenx.anilib.media.data.watch.MediaEpisodeBuilder
import com.revolgenx.anilib.media.data.watch.WatchAction
import com.revolgenx.anilib.media.data.watch.WatchActionStore
import com.revolgenx.anilib.media.data.watch.WatchEpisodeFilter
import com.revolgenx.anilib.media.data.watch.WatchInlineEngines
import com.revolgenx.anilib.media.data.watch.WatchPreference
import com.revolgenx.anilib.media.data.watch.WatchTorrentModel
import com.revolgenx.anilib.media.data.watch.WatchVars
import com.revolgenx.anilib.media.data.watch.site.WatchSiteMedia
import com.revolgenx.anilib.media.data.watch.site.WatchSiteResolvers
import com.revolgenx.anilib.media.data.watch.site.WatchSiteStore
import com.revolgenx.anilib.media.service.MediaInfoService
import com.revolgenx.anilib.media.service.WatchActionService
import com.revolgenx.anilib.media.source.MediaEpisodeSource

class MediaWatchViewModel(
    private val mediaInfoService: MediaInfoService,
    private val watchActionService: WatchActionService
) : BaseViewModel() {

    val field = MediaWatchField()

    var filter: WatchEpisodeFilter
        get() = WatchPreference.episodeFilter
        set(value) {
            WatchPreference.episodeFilter = value
        }

    private var model: MediaWatchModel? = null
    private var episodeActions: List<WatchAction> = emptyList()
    private var mediaActions: List<WatchAction> = emptyList()
    private var siteMedia = mutableMapOf<String, WatchSiteMedia>()
    private var loaded = false
    private var loading = false

    val sourceLiveData = MutableLiveData<MediaEpisodeSource>()
    val episodesLiveData = MutableLiveData<List<MediaEpisodeModel>>()

    val mediaType get() = model?.type

    fun load(force: Boolean = false) {
        if (loading) return
        if (loaded && !force) return

        loaded = true
        loading = true
        sourceLiveData.value = MediaEpisodeSource(Resource.loading(null))

        mediaInfoService.getMediaWatch(field, compositeDisposable) { resource ->
            loading = false
            when (resource) {
                is Resource.Success -> {
                    model = resource.data
                    siteMedia = WatchSiteStore.forMedia(field.mediaId).toMutableMap()
                    cacheActions()
                    rebuild()
                    resolveSites()
                }

                is Resource.Error -> {
                    loaded = false
                    sourceLiveData.value = MediaEpisodeSource(Resource.error(resource.message))
                }

                else -> {}
            }
        }
    }

    /** Chip toggles land here, the loaded media is only re-listed. */
    fun rebuild() {
        val current = model ?: return
        val episodes = MediaEpisodeBuilder.build(current, filter, siteEpisodes())

        episodesLiveData.value = episodes
        sourceLiveData.value = MediaEpisodeSource(Resource.success(episodes))
    }

    /** Every tile bind asks for its actions, they are resolved once per load instead. */
    fun actions(episodeScoped: Boolean): List<WatchAction> =
        if (episodeScoped) episodeActions else mediaActions

    fun defaultAction(episodeScoped: Boolean): WatchAction? {
        val applicable = actions(episodeScoped)
        return applicable.firstOrNull { it.isDefault } ?: applicable.firstOrNull()
    }

    /** The settings screen can rewrite the actions while the tab is alive. */
    fun refreshActions() {
        val previousEpisode = episodeActions
        val previousMedia = mediaActions

        cacheActions()
        if (previousEpisode != episodeActions || previousMedia != mediaActions) rebuild()
    }

    private fun cacheActions() {
        val mediaType = model?.type
        episodeActions = WatchActionStore.forMedia(mediaType, true)
        mediaActions = WatchActionStore.forMedia(mediaType, false)
    }

    fun urlOf(action: WatchAction, episode: MediaEpisodeModel?): String? {
        val current = model ?: return null
        return action.buildUrl(WatchVars.build(current, episode, action, siteMedia))
    }

    fun search(
        action: WatchAction,
        episode: MediaEpisodeModel?,
        callback: (Resource<List<WatchTorrentModel>>) -> Unit
    ) {
        val current = model ?: return
        val engine = WatchInlineEngines.of(action.siteKey) ?: return
        val url = action.buildInlineUrl(WatchVars.build(current, episode, action, siteMedia))
            ?: return

        watchActionService.search(url, engine, compositeDisposable, callback)
    }

    fun siteIdOf(siteKey: String): String? = siteMedia[siteKey]?.siteMediaId

    fun siteKeys(): List<String> = WatchActionStore.all()
        .mapNotNull { it.siteKey }
        .filter { WatchSiteResolvers.of(it) != null }
        .distinct()

    fun setSiteId(siteKey: String, value: String?) {
        val mediaId = field.mediaId

        if (value.isNullOrBlank()) {
            WatchSiteStore.remove(siteKey, mediaId)
            siteMedia.remove(siteKey)
        } else {
            val resolved = WatchSiteMedia(
                siteKey = siteKey,
                anilistId = mediaId,
                siteMediaId = value.trim(),
                episodes = siteMedia[siteKey]?.episodes.orEmpty(),
                manual = true
            )
            WatchSiteStore.put(resolved)
            siteMedia[siteKey] = resolved
        }

        rebuild()
        resolveSites()
    }

    private fun siteEpisodes() = siteMedia.values.firstOrNull { it.episodes.isNotEmpty() }
        ?.episodes
        .orEmpty()

    /** Site ids are only useful for a precise search, the tab never waits for them. */
    private fun resolveSites() {
        val current = model ?: return
        val siteKeys = WatchActionStore.all()
            .mapNotNull { it.siteKey }
            .distinct()
            .filterNot { siteMedia[it]?.episodes?.isNotEmpty() == true }

        siteKeys.forEach { siteKey ->
            watchActionService.resolveSiteMedia(siteKey, current, compositeDisposable) { resolved ->
                resolved ?: return@resolveSiteMedia
                siteMedia[siteKey] = resolved
                rebuild()
            }
        }
    }
}
