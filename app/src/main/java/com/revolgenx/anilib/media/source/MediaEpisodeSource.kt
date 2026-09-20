package com.revolgenx.anilib.media.source

import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.extensions.MainSource
import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.media.data.model.MediaEpisodeModel

class MediaEpisodeSource(val resource: Resource<List<MediaEpisodeModel>>) :
    MainSource<MediaEpisodeModel>() {

    override fun areItemsTheSame(first: MediaEpisodeModel, second: MediaEpisodeModel): Boolean {
        return first.batch == second.batch && first.number == second.number &&
                first.streamingUrl == second.streamingUrl
    }

    override fun onPageOpened(page: Page, dependencies: List<Element<*>>) {
        super.onPageOpened(page, dependencies)
        if (!page.isFirstPage()) return

        when (resource) {
            is Resource.Error -> postResult(page, Exception(resource.exception))
            is Resource.Loading -> {}
            is Resource.Success -> postResult(page, resource.data ?: emptyList())
        }
    }
}
