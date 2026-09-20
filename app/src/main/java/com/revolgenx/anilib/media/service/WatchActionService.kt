package com.revolgenx.anilib.media.service

import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.media.data.model.MediaWatchModel
import com.revolgenx.anilib.media.data.watch.WatchInlineEngine
import com.revolgenx.anilib.media.data.watch.WatchTorrentModel
import com.revolgenx.anilib.media.data.watch.site.WatchSiteMedia
import com.revolgenx.anilib.media.data.watch.site.WatchSiteResolvers
import com.revolgenx.anilib.media.data.watch.site.WatchSiteStore
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class WatchActionService(private val client: OkHttpClient) {

    fun resolveSiteMedia(
        siteKey: String,
        media: MediaWatchModel,
        compositeDisposable: CompositeDisposable?,
        callback: (WatchSiteMedia?) -> Unit
    ) {
        val cached = WatchSiteStore.get(siteKey, media.mediaId)
        val resolver = WatchSiteResolvers.of(siteKey)

        if (cached != null && (cached.episodes.isNotEmpty() || resolver == null)) {
            callback.invoke(cached)
            return
        }

        if (resolver == null) {
            callback.invoke(null)
            return
        }

        // a manually typed id has no episodes yet, only those are fetched then
        val disposable = Single.fromCallable {
            cached?.let { it.copy(episodes = resolver.episodes(client, it.siteMediaId)) }
                ?: resolver.resolve(client, media)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ resolved ->
                resolved?.let { WatchSiteStore.put(it) }
                callback.invoke(resolved)
            }, {
                Timber.w(it)
                callback.invoke(null)
            })

        compositeDisposable?.add(disposable)
    }

    fun search(
        url: String,
        engine: WatchInlineEngine,
        compositeDisposable: CompositeDisposable?,
        callback: (Resource<List<WatchTorrentModel>>) -> Unit
    ) {
        callback.invoke(Resource.loading(null))

        val disposable = Single.fromCallable {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                engine.parse(response.body?.string().orEmpty())
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                callback.invoke(Resource.success(it))
            }, {
                Timber.w(it)
                callback.invoke(Resource.error(it.message, null, it))
            })

        compositeDisposable?.add(disposable)
    }
}
