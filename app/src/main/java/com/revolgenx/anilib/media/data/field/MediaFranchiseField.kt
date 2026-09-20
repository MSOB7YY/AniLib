package com.revolgenx.anilib.media.data.field

import com.revolgenx.anilib.MediaFranchiseQuery
import com.revolgenx.anilib.common.data.field.BaseField
import com.revolgenx.anilib.constant.AlMediaRelation
import com.revolgenx.anilib.media.data.crawler.FranchiseCrawlLimits
import com.revolgenx.anilib.media.data.model.FranchiseNodeEdge
import com.revolgenx.anilib.media.data.model.FranchiseNodeModel
import com.revolgenx.anilib.media.data.model.toModel
import com.revolgenx.anilib.type.MediaRelation

class MediaFranchiseField : BaseField<MediaFranchiseQuery>() {
    var rootMediaId: Int = -1
    var rootMediaType: Int? = null
    var limits: FranchiseCrawlLimits = FranchiseCrawlLimits()
    var mediaIds: List<Int> = emptyList()

    override fun toQueryOrMutation(): MediaFranchiseQuery {
        // sorted ids keep the request body stable, so the offline cache can replay a crawl
        val ids = mediaIds.sorted()
        return MediaFranchiseQuery(
            idIn = nnList(ids),
            perPage = nn(ids.size.coerceIn(1, FranchiseCrawlLimits.PER_REQUEST))
        )
    }

    fun batchFor(ids: List<Int>) = MediaFranchiseField().also {
        it.rootMediaId = rootMediaId
        it.rootMediaType = rootMediaType
        it.limits = limits
        it.mediaIds = ids
    }
}

fun MediaFranchiseQuery.Data.toFranchiseNodes(): List<FranchiseNodeModel> =
    page?.media?.mapNotNull { medium ->
        medium ?: return@mapNotNull null
        FranchiseNodeModel(
            media = medium.mediaContent.toModel(),
            edges = medium.relations?.edges.orEmpty().mapNotNull inner@{ edge ->
                val node = edge?.node ?: return@inner null
                val relation = edge.relationType.toAlRelation() ?: return@inner null
                FranchiseNodeEdge(node.id, node.type?.ordinal, relation)
            }
        )
    }.orEmpty()

private fun MediaRelation?.toAlRelation() =
    this?.let { runCatching { AlMediaRelation.valueOf(it.rawValue) }.getOrNull() }
