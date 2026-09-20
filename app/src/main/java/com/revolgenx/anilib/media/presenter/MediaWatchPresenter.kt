package com.revolgenx.anilib.media.presenter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.pranavpandey.android.dynamic.support.widget.DynamicButton
import com.revolgenx.anilib.R
import com.revolgenx.anilib.app.theme.contrastAccentWithSurface
import com.revolgenx.anilib.common.presenter.BasePresenter
import com.revolgenx.anilib.databinding.MediaWatchPresenterBinding
import com.revolgenx.anilib.media.data.model.MediaEpisodeModel
import com.revolgenx.anilib.media.data.watch.WatchAction
import com.revolgenx.anilib.util.prettyTime
import java.util.concurrent.TimeUnit

class MediaWatchPresenter(
    context: Context,
    private val isAnime: () -> Boolean,
    private val coverImage: () -> String?,
    private val actionsOf: (MediaEpisodeModel) -> List<WatchAction>,
    private val onRun: (WatchAction, MediaEpisodeModel) -> Unit,
    private val onMenu: (MediaEpisodeModel) -> Unit
) : BasePresenter<MediaWatchPresenterBinding, MediaEpisodeModel>(context) {

    override val elementTypes: Collection<Int>
        get() = listOf(0)

    private val watchedColor by lazy { ContextCompat.getColor(context, R.color.watch_watched_color) }
    private val separatorColor by lazy { ColorUtils.setAlphaComponent(contrastAccentWithSurface, SEPARATOR_ALPHA) }
    private val thumbnailWidth by lazy { context.resources.getDimensionPixelSize(R.dimen.watch_thumbnail_width) }
    private val emphasizedWidth by lazy { context.resources.getDimensionPixelSize(R.dimen.watch_thumbnail_width_large) }

    override fun bindView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        elementType: Int
    ): MediaWatchPresenterBinding {
        return MediaWatchPresenterBinding.inflate(inflater, parent, false)
    }

    override fun onBind(page: Page, holder: Holder, element: Element<MediaEpisodeModel>) {
        super.onBind(page, holder, element)
        val item = element.data ?: return

        holder.getBinding()?.apply {
            watchContentLayout.alpha = if (item.aired) 1f else UNAIRED_ALPHA

            watchMarkerView.setBackgroundColor(
                when {
                    item.nextToWatch -> contrastAccentWithSurface
                    item.watched -> watchedColor
                    else -> Color.TRANSPARENT
                }
            )

            // the drawee is never hidden, a recycled hidden hierarchy stops refetching its image
            val image = item.thumbnail?.takeIf { it.isNotBlank() } ?: coverImage.invoke()
            if (watchThumbnailIv.tag != image) {
                watchThumbnailIv.tag = image
                watchThumbnailIv.setImageURI(image)
            }
            watchNumberTv.visibility = if (image == null) View.VISIBLE else View.GONE
            watchNumberTv.text = item.number?.toString() ?: ALL_LABEL

            val width = if (item.emphasized) emphasizedWidth else thumbnailWidth
            if (watchThumbnailContainer.layoutParams.width != width) {
                watchThumbnailContainer.layoutParams =
                    watchThumbnailContainer.layoutParams.also { it.width = width }
            }

            watchTitleTv.text = titleOf(item)

            val subtitle = subtitleOf(item)
            watchSubtitleTv.visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
            watchSubtitleTv.text = subtitle

            watchSeparatorView.visibility = if (item.batch) View.VISIBLE else View.GONE
            if (item.batch) watchSeparatorView.setBackgroundColor(separatorColor)

            watchMenuIv.setOnClickListener { onMenu.invoke(item) }
            watchContainer.setOnLongClickListener {
                onMenu.invoke(item)
                true
            }

            val actions = actionsOf.invoke(item)
            watchContainer.setOnClickListener {
                val action = actions.firstOrNull { it.isDefault } ?: actions.firstOrNull()
                action?.let { onRun.invoke(it, item) }
            }

            bindActions(watchActionContainer, actions, item)
        }
    }

    /** The button row is rebuilt only when the action count changes, binds are frequent. */
    private fun bindActions(
        container: ViewGroup,
        actions: List<WatchAction>,
        item: MediaEpisodeModel
    ) {
        if (container.childCount != actions.size) {
            container.removeAllViews()
            repeat(actions.size) {
                container.addView(
                    getLayoutInflater().inflate(R.layout.media_watch_action_button, container, false)
                )
            }
        }

        actions.forEachIndexed { index, action ->
            val button = container.getChildAt(index) as? DynamicButton ?: return@forEachIndexed
            button.text = action.name
            button.setOnClickListener { onRun.invoke(action, item) }
        }
    }

    private fun titleOf(item: MediaEpisodeModel): String = when {
        item.batch -> context.getString(R.string.watch_all_episodes)
        item.number == null -> item.title.orEmpty()
        isAnime.invoke() -> context.getString(R.string.watch_episode).format(item.number)
        else -> context.getString(R.string.watch_chapter).format(item.number)
    }

    private fun subtitleOf(item: MediaEpisodeModel): String {
        if (item.batch) return ""

        val parts = mutableListOf<String>()
        if (item.number != null) item.title?.takeIf { it.isNotBlank() }?.let { parts += it }

        val airingAt = item.airingAt
        when {
            item.nextAiring && airingAt != null -> parts += context.getString(R.string.watch_airs_in)
                .format(countdown(airingAt))

            !item.aired -> parts += context.getString(R.string.watch_not_aired)
            item.watched -> parts += context.getString(R.string.watch_watched)
            item.nextToWatch -> parts += context.getString(R.string.watch_next_up)
            airingAt != null -> parts += airingAt.prettyTime()
        }

        item.streamingSite?.takeIf { it.isNotBlank() }?.let { parts += it }

        return parts.joinToString(" · ")
    }

    private fun countdown(airingAt: Long): String {
        val seconds = airingAt - System.currentTimeMillis() / 1000
        if (seconds <= 0) return ""

        val days = TimeUnit.SECONDS.toDays(seconds)
        val hours = TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(
            TimeUnit.SECONDS.toHours(seconds)
        )

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    companion object {
        private const val UNAIRED_ALPHA = 0.45f
        private const val SEPARATOR_ALPHA = 140
        private const val ALL_LABEL = "★"
    }
}
