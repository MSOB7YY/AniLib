package com.revolgenx.anilib.media.source

import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.extensions.MainSource
import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.media.data.order.MediaChronologyModel

class MediaChronologySource(val resource: Resource<List<MediaChronologyModel>>) :
    MainSource<MediaChronologyModel>() {

    override fun areItemsTheSame(first: MediaChronologyModel, second: MediaChronologyModel): Boolean {
        return first.media.id == second.media.id
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
