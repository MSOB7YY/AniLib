package com.revolgenx.anilib.common.repository.network

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.atomic.AtomicBoolean

/** Tells the ui whether the last response came from [OfflineResponseCache] instead of the network. */
object OfflineCacheState {

    private val cached = AtomicBoolean(false)
    private val _servingCachedData = MutableLiveData(false)

    val servingCachedData: LiveData<Boolean> = _servingCachedData

    fun report(fromCache: Boolean) {
        if (cached.getAndSet(fromCache) != fromCache) {
            _servingCachedData.postValue(fromCache)
        }
    }
}
