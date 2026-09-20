package com.revolgenx.anilib.media.data.crawler

import com.revolgenx.anilib.constant.AlMediaRelation
import com.revolgenx.anilib.media.data.model.FranchiseEdge
import com.revolgenx.anilib.media.data.model.FranchiseGraph
import com.revolgenx.anilib.media.data.model.FranchiseNodeEdge
import com.revolgenx.anilib.media.data.model.FranchiseNodeModel
import com.revolgenx.anilib.media.data.model.FranchisePending
import com.revolgenx.anilib.media.data.model.FranchiseTruncation
import com.revolgenx.anilib.media.data.model.MediaModel
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class FranchiseCrawlLimits(
    val maxMedia: Int = DEFAULT_MAX_MEDIA,
    val maxDepth: Int = 10,
    val maxRequests: Int = 12,
    val requestSpacingMs: Long = 250,
) {
    companion object {
        const val PER_REQUEST = 50
        const val DEFAULT_MAX_MEDIA = 100
        const val DEPTH_STEP = 5
        const val RETRY_DELAY_MS = 2000L
    }
}

object FranchiseRelations {
    val TRAVERSABLE = setOf(
        AlMediaRelation.PREQUEL,
        AlMediaRelation.SEQUEL,
        AlMediaRelation.PARENT,
        AlMediaRelation.SIDE_STORY,
        AlMediaRelation.ALTERNATIVE,
        AlMediaRelation.SPIN_OFF,
        AlMediaRelation.SUMMARY,
        AlMediaRelation.COMPILATION,
        AlMediaRelation.CONTAINS,
    )

    /** The only relations that assert a watch order. */
    val ORDERING = setOf(
        AlMediaRelation.PREQUEL,
        AlMediaRelation.SEQUEL,
        AlMediaRelation.PARENT,
        AlMediaRelation.SIDE_STORY,
    )
}

/**
 * Breadth first expansion of the relation graph, one batched request per chunk of ids.
 * Only media of the root type are kept, and CHARACTER and OTHER edges are dropped outright,
 * they transitively link most of anime together.
 */
class FranchiseCrawler(
    private val limits: FranchiseCrawlLimits,
    private val rootType: Int?,
    private val fetch: (List<Int>) -> Single<List<FranchiseNodeModel>>,
) {

    fun crawl(rootId: Int): Single<FranchiseGraph> =
        Single.defer { round(CrawlState(rootId, limits, rootType)) }
            .subscribeOn(Schedulers.io())

    fun resume(graph: FranchiseGraph): Single<FranchiseGraph> =
        Single.defer { round(CrawlState(graph, limits, rootType)) }
            .subscribeOn(Schedulers.io())

    private fun round(state: CrawlState): Single<FranchiseGraph> {
        val frontier = state.takeFrontier()
        if (frontier.isEmpty()) return Single.just(state.toGraph())

        val isFirstRound = state.requests == 0

        return Observable.fromIterable(frontier.chunked(FranchiseCrawlLimits.PER_REQUEST))
            .concatMapSingle { ids ->
                fetch(ids)
                    .delaySubscription(
                        if (isFirstRound) 0 else limits.requestSpacingMs,
                        TimeUnit.MILLISECONDS
                    )
                    .retryOnce()
                    .let { single ->
                        if (isFirstRound) single else single.onErrorReturn {
                            state.markTruncated(FranchiseTruncation.PARTIAL_FAILURE)
                            emptyList()
                        }
                    }
                    .doOnSuccess { state.requests++ }
            }
            .toList()
            .map { rounds -> state.absorb(rounds.flatten()) }
            .flatMap { round(it) }
    }

    private fun <T> Single<T>.retryOnce(): Single<T> {
        val retried = AtomicBoolean()
        return retryWhen { errors ->
            errors.flatMap { error ->
                if (retried.compareAndSet(false, true)) {
                    Flowable.timer(FranchiseCrawlLimits.RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
                } else {
                    Flowable.error(error)
                }
            }
        }
    }
}

private class CrawlState(
    val rootId: Int,
    val limits: FranchiseCrawlLimits,
    val rootType: Int?,
) {
    val nodes = LinkedHashMap<Int, MediaModel>()
    val edges = mutableListOf<FranchiseEdge>()
    val discoveredVia = LinkedHashMap<Int, AlMediaRelation>()

    private val visited = mutableSetOf<Int>()
    private val pending = LinkedHashSet<Int>()

    var depth = 0
    var requests = 0
    private var truncation: FranchiseTruncation? = null

    init {
        pending.add(rootId)
    }

    constructor(graph: FranchiseGraph, limits: FranchiseCrawlLimits, rootType: Int?) : this(
        graph.rootId,
        limits,
        rootType
    ) {
        nodes.putAll(graph.nodes)
        edges.addAll(graph.edges)
        discoveredVia.putAll(graph.discoveredVia)
        visited.addAll(graph.nodes.keys)
        pending.clear()
        graph.pending?.let {
            pending.addAll(it.ids)
            depth = it.depth
        }
    }

    fun markTruncated(reason: FranchiseTruncation) {
        if (truncation == null) truncation = reason
    }

    fun takeFrontier(): List<Int> {
        if (pending.isEmpty()) return emptyList()

        if (depth > limits.maxDepth) {
            markTruncated(FranchiseTruncation.MAX_DEPTH)
            return emptyList()
        }
        if (requests >= limits.maxRequests) {
            markTruncated(FranchiseTruncation.MAX_REQUESTS)
            return emptyList()
        }

        val remaining = limits.maxMedia - nodes.size
        if (remaining <= 0) {
            markTruncated(FranchiseTruncation.MAX_MEDIA)
            return emptyList()
        }

        if (pending.size > remaining) markTruncated(FranchiseTruncation.MAX_MEDIA)

        val frontier = pending.sorted().take(remaining)
        visited.addAll(frontier)
        pending.removeAll(frontier.toSet())
        return frontier
    }

    fun absorb(fetched: List<FranchiseNodeModel>): CrawlState {
        fetched.forEach { node ->
            val media = node.media
            if (rootType != null && media.type != rootType) return@forEach

            nodes[media.id] = media
            node.edges.forEach { edge -> absorbEdge(media.id, edge) }
        }

        depth++
        return this
    }

    private fun absorbEdge(fromId: Int, edge: FranchiseNodeEdge) {
        if (edge.relation !in FranchiseRelations.TRAVERSABLE) return
        if (rootType != null && edge.toType != null && edge.toType != rootType) return

        edges.add(FranchiseEdge(fromId, edge.toId, edge.relation))

        if (edge.toId in visited || edge.toId in pending) return

        discoveredVia[edge.toId] = edge.relation
        pending.add(edge.toId)
    }

    fun toGraph() = FranchiseGraph(
        rootId = rootId,
        nodes = nodes,
        edges = edges,
        discoveredVia = discoveredVia,
        truncation = truncation,
        pending = FranchisePending(pending.toList(), depth)
    )
}
