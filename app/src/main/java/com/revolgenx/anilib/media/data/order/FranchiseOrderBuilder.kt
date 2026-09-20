package com.revolgenx.anilib.media.data.order

import com.revolgenx.anilib.constant.AlMediaRelation
import com.revolgenx.anilib.media.data.crawler.FranchiseRelations
import com.revolgenx.anilib.media.data.model.FranchiseEdge
import com.revolgenx.anilib.media.data.model.FranchiseGraph
import com.revolgenx.anilib.media.data.model.FranchiseTruncation
import com.revolgenx.anilib.media.data.model.MediaModel
import com.revolgenx.anilib.type.MediaFormat
import com.revolgenx.anilib.type.MediaSeason
import java.util.PriorityQueue

enum class FranchiseOrderMode { RELEASE, STORY }

data class FranchiseFilter(
    val includeSideStories: Boolean = true,
    val includeSpecials: Boolean = true,
    val onMyListOnly: Boolean = false,
    val showAdult: Boolean = true,
)

class MediaChronologyModel(
    val position: Int,
    val media: MediaModel,
    val relation: AlMediaRelation?,
    val isRoot: Boolean,
)

data class FranchiseSummary(
    val entries: Int,
    val episodes: Int,
    val minutes: Int,
    val chapters: Int,
    val truncation: FranchiseTruncation?,
    val canLoadMore: Boolean,
)

object FranchiseOrderBuilder {

    private const val UNDATED = Long.MAX_VALUE - 1

    private val SPECIAL_FORMATS = setOf(
        MediaFormat.SPECIAL.ordinal,
        MediaFormat.OVA.ordinal,
        MediaFormat.ONA.ordinal,
        MediaFormat.MUSIC.ordinal,
    )

    private val SIDE_RELATIONS = setOf(AlMediaRelation.SIDE_STORY, AlMediaRelation.SPIN_OFF)

    fun build(
        graph: FranchiseGraph,
        mode: FranchiseOrderMode,
        filter: FranchiseFilter,
    ): List<MediaChronologyModel> {
        val visible = LinkedHashMap<Int, MediaModel>(graph.nodes.size)
        graph.nodes.values.forEach { if (keep(graph, it, filter)) visible[it.id] = it }

        if (visible.isEmpty()) return emptyList()

        val keys = HashMap<Int, Long>(visible.size)
        visible.forEach { (id, media) -> keys[id] = releaseKey(media) }

        val ordered = when (mode) {
            FranchiseOrderMode.RELEASE -> visible.keys
                .sortedWith(compareBy({ keys.getValue(it) }, { it }))
            FranchiseOrderMode.STORY -> storyOrder(visible.keys, keys, graph.edges)
        }

        return ordered.mapIndexed { index, id ->
            MediaChronologyModel(
                position = index + 1,
                media = visible.getValue(id),
                relation = graph.discoveredVia[id],
                isRoot = id == graph.rootId
            )
        }
    }

    fun summarize(rows: List<MediaChronologyModel>, graph: FranchiseGraph): FranchiseSummary {
        var episodes = 0
        var minutes = 0
        var chapters = 0

        rows.forEach { row ->
            val media = row.media
            episodes += media.episodes ?: 0
            chapters += media.chapters ?: 0
            media.duration?.let { minutes += (media.episodes ?: 1) * it }
        }

        return FranchiseSummary(
            entries = rows.size,
            episodes = episodes,
            minutes = minutes,
            chapters = chapters,
            truncation = graph.truncation,
            canLoadMore = graph.canLoadMore
        )
    }

    private fun keep(graph: FranchiseGraph, media: MediaModel, filter: FranchiseFilter): Boolean {
        if (media.id == graph.rootId) return true
        if (media.isAdult && !filter.showAdult) return false

        val relation = graph.discoveredVia[media.id]
        if (!filter.includeSideStories && relation in SIDE_RELATIONS) return false
        if (!filter.includeSpecials && media.format in SPECIAL_FORMATS) return false
        if (filter.onMyListOnly && media.mediaListEntry?.status == null) return false

        return true
    }

    /**
     * Kahn over the relations that actually claim an order, with a release-date priority queue as
     * the ready set so disconnected branches interleave by air date and the result is stable.
     */
    internal fun storyOrder(
        nodeIds: Set<Int>,
        keys: Map<Int, Long>,
        edges: List<FranchiseEdge>,
    ): List<Int> {
        val outgoing = HashMap<Int, MutableSet<Int>>(nodeIds.size)
        val inDegree = HashMap<Int, Int>(nodeIds.size)
        nodeIds.forEach { inDegree[it] = 0 }

        edges.forEach { edge ->
            if (edge.relation !in FranchiseRelations.ORDERING) return@forEach
            if (edge.fromId !in nodeIds || edge.toId !in nodeIds) return@forEach

            val before: Int
            val after: Int
            if (edge.relation == AlMediaRelation.SEQUEL || edge.relation == AlMediaRelation.SIDE_STORY) {
                before = edge.fromId
                after = edge.toId
            } else {
                before = edge.toId
                after = edge.fromId
            }

            // AniList states a relation from both sides, so the same arc arrives twice
            if (outgoing.getOrPut(before) { mutableSetOf() }.add(after)) {
                inDegree[after] = inDegree.getValue(after) + 1
            }
        }

        val comparator = compareBy<Int>({ keys.getValue(it) }, { it })
        val ready = PriorityQueue(comparator)
        nodeIds.forEach { if (inDegree.getValue(it) == 0) ready.add(it) }

        val order = ArrayList<Int>(nodeIds.size)
        val emitted = HashSet<Int>(nodeIds.size)

        while (emitted.size < nodeIds.size) {
            if (ready.isEmpty()) {
                // cycle: cut at the earliest airing member of the least constrained remainder
                val victim = nodeIds.asSequence()
                    .filter { it !in emitted }
                    .minWithOrNull(
                        compareBy({ inDegree.getValue(it) }, { keys.getValue(it) }, { it })
                    ) ?: break
                ready.add(victim)
            }

            val id = ready.poll() ?: break
            if (!emitted.add(id)) continue
            order.add(id)

            outgoing[id]?.forEach { next ->
                if (next in emitted) return@forEach
                val left = inDegree.getValue(next) - 1
                inDegree[next] = left
                if (left <= 0) ready.add(next)
            }
        }

        return order
    }

    internal fun releaseKey(media: MediaModel): Long {
        val year = media.startDate?.year ?: media.seasonYear ?: return UNDATED
        val month = media.startDate?.month ?: seasonMonth(media.season) ?: 13
        val day = media.startDate?.day ?: 32
        return year * 10_000L + month * 100L + day
    }

    private fun seasonMonth(season: Int?) = when (season) {
        MediaSeason.WINTER.ordinal -> 1
        MediaSeason.SPRING.ordinal -> 4
        MediaSeason.SUMMER.ordinal -> 7
        MediaSeason.FALL.ordinal -> 10
        else -> null
    }
}
