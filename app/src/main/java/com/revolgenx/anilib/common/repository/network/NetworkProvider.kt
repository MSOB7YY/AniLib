package com.revolgenx.anilib.common.repository.network

import android.content.Context
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.revolgenx.anilib.BuildConfig
import com.revolgenx.anilib.common.preference.loggedIn
import com.revolgenx.anilib.common.preference.token
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File

object NetworkProvider {
    private const val ANILIST_API_URL = "https://graphql.anilist.co"
    private const val OFFLINE_CACHE_DIR = "graphql_offline_cache"
    private const val OFFLINE_CACHE_SIZE = 200L * 1024 * 1024

    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient().newBuilder().build()
    }

    fun provideApolloClient(context: Context): ApolloClient = ApolloClient.Builder()
        .okHttpClient(
            OkHttpClient.Builder()
                .addInterceptor(
                    OfflineCacheInterceptor(
                        OfflineResponseCache(
                            File(context.cacheDir, OFFLINE_CACHE_DIR),
                            OFFLINE_CACHE_SIZE
                        )
                    )
                )
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
                })
                .addInterceptor {
                    if (!loggedIn()) {
                        it.proceed(it.request())
                    } else {
                        val request = it.request()
                        val newRequest: Request = request.newBuilder()
                            .addHeader(
                                "Authorization",
                                "Bearer ${token()}"
                            )
                            .build()
                        it.proceed(newRequest)
                    }
                }
                .build())
        .serverUrl(ANILIST_API_URL)
        .build()
}
