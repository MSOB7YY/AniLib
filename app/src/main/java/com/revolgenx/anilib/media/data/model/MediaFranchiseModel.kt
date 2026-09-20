package com.revolgenx.anilib.media.data.model

import com.revolgenx.anilib.constant.AlMediaRelation

/**
 * AniList labels the target of a relation, so an edge on [fromId] typed [SEQUEL] pointing at
 * [toId] means "[toId] is a sequel of [fromId]".
 */
data class FranchiseEdge(val fromId: Int, val toId: Int, val relation: AlMediaRelation)

data class FranchiseNodeEdge(val toId: Int, val toType: Int?, val relation: AlMediaRelation)

class FranchiseNodeModel(val media: MediaModel, val edges: List<FranchiseNodeEdge>)

enum class FranchiseTruncation { MAX_MEDIA, MAX_DEPTH, MAX_REQUESTS, PARTIAL_FAILURE }

/** Frontier left over when a crawl stops on a cap, so it can be resumed on demand. */
class FranchisePending(
    val ids: List<Int>,
    val depth: Int,
)

class FranchiseGraph(
    val rootId: Int,
    val nodes: Map<Int, MediaModel>,
    val edges: List<FranchiseEdge>,
    val discoveredVia: Map<Int, AlMediaRelation>,
    val truncation: FranchiseTruncation? = null,
    val pending: FranchisePending? = null,
) {
    val canLoadMore get() = pending?.ids?.isNotEmpty() ?: false
}
