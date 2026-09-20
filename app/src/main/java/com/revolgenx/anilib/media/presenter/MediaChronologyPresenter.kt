package com.revolgenx.anilib.media.presenter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.data.model.FuzzyDateModel
import com.revolgenx.anilib.common.event.OpenMediaInfoEvent
import com.revolgenx.anilib.common.presenter.BasePresenter
import com.revolgenx.anilib.databinding.MediaChronologyPresenterLayoutBinding
import com.revolgenx.anilib.media.data.meta.MediaInfoMeta
import com.revolgenx.anilib.media.data.model.MediaModel
import com.revolgenx.anilib.media.data.order.FranchiseFormat
import com.revolgenx.anilib.media.data.order.MediaChronologyModel
import com.revolgenx.anilib.util.naText
import java.text.DateFormatSymbols

class MediaChronologyPresenter(context: Context) :
    BasePresenter<MediaChronologyPresenterLayoutBinding, MediaChronologyModel>(context) {

    override val elementTypes: Collection<Int>
        get() = listOf(0)

    private val relations by lazy { context.resources.getStringArray(R.array.media_relation) }
    private val formats by lazy { context.resources.getStringArray(R.array.media_format) }
    private val statuses by lazy { context.resources.getStringArray(R.array.media_status) }
    private val seasons by lazy { context.resources.getStringArray(R.array.media_season) }
    private val animeListStatus by lazy { context.resources.getStringArray(R.array.anime_list_status) }
    private val mangaListStatus by lazy { context.resources.getStringArray(R.array.manga_list_status) }
    private val shortMonths by lazy { DateFormatSymbols.getInstance().shortMonths }

    override fun bindView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        elementType: Int
    ): MediaChronologyPresenterLayoutBinding {
        return MediaChronologyPresenterLayoutBinding.inflate(inflater, parent, false)
    }

    override fun onBind(page: Page, holder: Holder, element: Element<MediaChronologyModel>) {
        super.onBind(page, holder, element)
        val item = element.data ?: return
        val media = item.media

        holder.getBinding()?.apply {
            chronologyPositionTv.text = item.position.toString()
            chronologyTitleTv.text = media.title?.title().naText()
            chronologyCoverIv.setImageURI(media.coverImage?.image())
            chronologyScoreBadge.text = media.averageScore

            chronologyRelationTv.text = context.getString(R.string.str_dot_str).format(
                if (item.isRoot) {
                    context.getString(R.string.current)
                } else {
                    relations.getOrNull(item.relation?.ordinal ?: -1).naText()
                },
                formats.getOrNull(media.format ?: -1).naText()
            )

            chronologyMetaTv.text = metaOf(media)
            chronologyDateTv.text = dateOf(media)

            val listStatus = media.mediaListEntry?.status
            chronologyListStatusTv.status = listStatus
            chronologyListStatusTv.text = listStatus?.let { status ->
                val labels = if (media.isAnime) animeListStatus else mangaListStatus
                val total = if (media.isAnime) media.episodes else media.chapters
                "%s %d/%s".format(
                    labels.getOrNull(status).naText(),
                    media.mediaListEntry?.progress ?: 0,
                    total.naText()
                )
            } ?: ""

            root.setOnClickListener {
                OpenMediaInfoEvent(
                    MediaInfoMeta(
                        media.id,
                        media.type,
                        media.title?.romaji,
                        media.coverImage?.image(),
                        media.coverImage?.largeImage,
                        media.bannerImage
                    )
                ).postEvent
            }
        }
    }

    private fun metaOf(media: MediaModel): String {
        if (!media.isAnime) {
            return context.getString(R.string.chapter_volume_count)
                .format(media.chapters.naText(), media.volumes.naText())
        }

        val episodes = media.episodes ?: return statuses.getOrNull(media.status ?: -1).naText()
        val duration = media.duration
            ?: return context.getString(R.string.episodes) + " " + episodes

        return context.getString(R.string.str_dot_str).format(
            context.getString(R.string.episode_count_duration).format(episodes, duration),
            FranchiseFormat.runtime(context, episodes * duration)
        )
    }

    private fun dateOf(media: MediaModel): String {
        val status = statuses.getOrNull(media.status ?: -1).naText()
        val start = media.startDate.label()
            ?: media.seasonYear?.let { year ->
                media.season?.let { seasons.getOrNull(it) }?.let { "$it $year" } ?: year.toString()
            }
            ?: return status

        val end = media.endDate.label()
        val range = if (end == null || end == start) start else "$start – $end"
        return context.getString(R.string.str_dot_str).format(range, status)
    }

    private fun FuzzyDateModel?.label(): String? {
        val year = this?.year ?: return null
        val month = this.month?.let { shortMonths.getOrNull(it - 1) }
        return if (month.isNullOrEmpty()) year.toString() else "$month $year"
    }
}
