package com.revolgenx.anilib.media.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Presenter
import com.otaliastudios.elements.pagers.NoPagesPager
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.ui.fragment.BaseLayoutFragment
import com.revolgenx.anilib.databinding.MediaWatchFragmentBinding
import com.revolgenx.anilib.media.bottomsheet.WatchActionBottomSheet
import com.revolgenx.anilib.media.bottomsheet.WatchSheetExtra
import com.revolgenx.anilib.media.data.meta.MediaInfoMeta
import com.revolgenx.anilib.media.data.model.MediaEpisodeModel
import com.revolgenx.anilib.media.data.watch.MediaEpisodeBuilder
import com.revolgenx.anilib.media.data.watch.WatchAction
import com.revolgenx.anilib.media.data.watch.WatchEpisodeFilter
import com.revolgenx.anilib.media.data.watch.site.WatchSiteResolvers
import com.revolgenx.anilib.media.presenter.MediaWatchPresenter
import com.revolgenx.anilib.media.viewmodel.MediaWatchViewModel
import com.revolgenx.anilib.type.MediaType
import com.revolgenx.anilib.ui.dialog.InputDialog
import com.revolgenx.anilib.ui.view.widgets.chip.MediaListGroupChip
import com.revolgenx.anilib.util.copyToClipBoard
import com.revolgenx.anilib.util.getParcelableCompat
import com.revolgenx.anilib.util.openLink
import com.revolgenx.anilib.util.shareText
import org.koin.androidx.viewmodel.ext.android.viewModel

class MediaWatchFragment : BaseLayoutFragment<MediaWatchFragmentBinding>() {

    private val viewModel by viewModel<MediaWatchViewModel>()
    private var coverImage: String? = null
    private var pendingScrollIndex = -1
    private var scrollFabIcon = 0

    private val watchPresenter by lazy {
        MediaWatchPresenter(
            requireContext(),
            isAnime = { viewModel.mediaType != MediaType.MANGA.ordinal },
            coverImage = { coverImage },
            actionsOf = { episode -> viewModel.actions(episode.episodeScoped) },
            onRun = { action, episode -> runAction(action, episode) },
            onMenu = { episode -> showSheet(episode) }
        )
    }

    private val loadingPresenter: Presenter<Unit> by lazy {
        Presenter.forLoadingIndicator(requireContext(), R.layout.loading_layout)
    }

    private val errorPresenter: Presenter<Unit> by lazy {
        Presenter.forErrorIndicator(requireContext(), R.layout.error_layout)
    }

    private val emptyPresenter: Presenter<Unit> by lazy {
        Presenter.forEmptyIndicator(requireContext(), R.layout.empty_layout)
    }

    companion object {
        private const val MEDIA_INFO_META_KEY = "MEDIA_INFO_META_KEY"
        private const val SCROLL_DONE = -2

        fun newInstance(meta: MediaInfoMeta) = MediaWatchFragment().also {
            it.arguments = bundleOf(MEDIA_INFO_META_KEY to meta)
        }
    }

    private val MediaEpisodeModel.episodeScoped get() = !batch && number != null

    override fun bindView(inflater: LayoutInflater, parent: ViewGroup?) =
        MediaWatchFragmentBinding.inflate(inflater, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val meta = arguments?.getParcelableCompat<MediaInfoMeta>(MEDIA_INFO_META_KEY) ?: return
        val mediaId = meta.mediaId ?: return

        viewModel.field.mediaId = mediaId
        coverImage = meta.coverImageLarge ?: meta.coverImage

        val layoutManager = LinearLayoutManager(requireContext())
        binding.watchRecyclerView.layoutManager = layoutManager

        viewModel.episodesLiveData.observe(viewLifecycleOwner) { episodes ->
            if (pendingScrollIndex == SCROLL_DONE) return@observe
            pendingScrollIndex = MediaEpisodeBuilder.nextToWatchIndex(episodes)
        }

        viewModel.sourceLiveData.observe(viewLifecycleOwner) { source ->
            binding.watchSwipeToRefresh.isRefreshing = false

            // a resolved site id rebuilds the list, the reader should not be thrown back to the top
            val position = layoutManager.findFirstVisibleItemPosition()

            Adapter.builder(this)
                .setPager(NoPagesPager())
                .addSource(source)
                .addPresenter(watchPresenter)
                .addPresenter(loadingPresenter)
                .addPresenter(errorPresenter)
                .addPresenter(emptyPresenter)
                .into(binding.watchRecyclerView)

            val index = if (pendingScrollIndex > 0) pendingScrollIndex else position
            if (index > 0) scrollWhenReady(index)
            if (pendingScrollIndex > 0) pendingScrollIndex = SCROLL_DONE
        }

        binding.watchSwipeToRefresh.setOnRefreshListener {
            binding.watchSwipeToRefresh.isRefreshing = false
            viewModel.load(force = true)
        }

        binding.watchScrollFab.setOnClickListener {
            val count = binding.watchRecyclerView.adapter?.itemCount ?: return@setOnClickListener
            if (count == 0) return@setOnClickListener

            binding.watchRecyclerView.scrollToPosition(if (scrollsDown(layoutManager)) count - 1 else 0)
            updateScrollFab(layoutManager)
        }

        binding.watchRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateScrollFab(layoutManager)
            }
        })

        renderFilterChips()

        viewModel.load()
        viewModel.rebuild()
    }

    private fun scrollsDown(layoutManager: LinearLayoutManager): Boolean {
        val count = binding.watchRecyclerView.adapter?.itemCount ?: 0
        return layoutManager.findFirstVisibleItemPosition() < count / 2
    }

    private fun updateScrollFab(layoutManager: LinearLayoutManager) {
        val icon = if (scrollsDown(layoutManager)) {
            R.drawable.ic_arrow_down
        } else {
            R.drawable.ic_baseline_keyboard_arrow_up
        }

        if (scrollFabIcon == icon) return

        scrollFabIcon = icon
        binding.watchScrollFab.setImageResource(icon)
    }

    /** Elements diffs off the main thread, the rows are not there right after the adapter is set. */
    private fun scrollWhenReady(index: Int) {
        val recyclerView = binding.watchRecyclerView
        val adapter = recyclerView.adapter ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        if (adapter.itemCount > index) {
            layoutManager.scrollToPositionWithOffset(index, 0)
            return
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = scroll()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = scroll()

            private fun scroll() {
                if (adapter.itemCount <= index) return

                adapter.unregisterAdapterDataObserver(this)
                layoutManager.scrollToPositionWithOffset(index, 0)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshActions()
    }

    private fun renderFilterChips() {
        val group = binding.watchFilterChipGroup

        WatchFilterChip.values().forEach { chipType ->
            val chip = layoutInflater.inflate(
                R.layout.media_chronology_chip,
                group,
                false
            ) as MediaListGroupChip

            chip.tag = chipType
            chip.setText(chipType.labelRes)
            chip.chipIcon = AppCompatResources.getDrawable(requireContext(), chipType.iconRes)
            chip.isChecked = chipType.isOn(viewModel.filter)
            chip.setOnClickListener {
                viewModel.filter = chipType.toggle(viewModel.filter)
                viewModel.rebuild()
                updateChipStates()
            }

            group.addView(chip)
        }
    }

    private fun updateChipStates() {
        binding.watchFilterChipGroup.children.forEach { chip ->
            val chipType = chip.tag as? WatchFilterChip ?: return@forEach
            (chip as MediaListGroupChip).isChecked = chipType.isOn(viewModel.filter)
        }
    }

    private fun runAction(action: WatchAction, episode: MediaEpisodeModel) {
        if (action.inlineResults && action.supportsInline) {
            showSheet(episode, action)
            return
        }

        openLink(viewModel.urlOf(action, episode.target()))
    }

    private fun showSheet(episode: MediaEpisodeModel, initialAction: WatchAction? = null) {
        val target = episode.target()

        WatchActionBottomSheet().also { sheet ->
            sheet.headerTitle = titleOf(episode)
            sheet.actions = viewModel.actions(episode.episodeScoped)
            sheet.initialAction = initialAction
            sheet.extras = extrasOf(episode)
            sheet.urlOf = { action -> viewModel.urlOf(action, target) }
            sheet.onOpenLink = { url -> openLink(url) }
            sheet.onSearch = { action, callback -> viewModel.search(action, target, callback) }
            sheet.onCopyLink = { link -> requireContext().copyToClipBoard(link) }
        }.show(this)
    }

    private fun extrasOf(episode: MediaEpisodeModel): List<WatchSheetExtra> {
        val extras = mutableListOf<WatchSheetExtra>()
        val target = episode.target()

        episode.streamingUrl?.takeIf { it.isNotBlank() }?.let { url ->
            extras += WatchSheetExtra(
                title = getString(R.string.watch_stream_on).format(
                    episode.streamingSite.orEmpty()
                ),
                subtitle = url,
                icon = R.drawable.ic_play
            ) { openLink(url) }
        }

        val defaultUrl = viewModel.defaultAction(episode.episodeScoped)
            ?.let { viewModel.urlOf(it, target) }

        defaultUrl?.let { url ->
            extras += WatchSheetExtra(
                getString(R.string.watch_copy_link),
                null,
                R.drawable.ic_copy
            ) { requireContext().copyToClipBoard(url) }

            extras += WatchSheetExtra(
                getString(R.string.watch_share_link),
                null,
                R.drawable.ic_share
            ) { shareText(url) }
        }

        viewModel.siteKeys().forEach { siteKey ->
            extras += WatchSheetExtra(
                title = getString(R.string.str_dot_str).format(
                    getString(R.string.watch_site_id),
                    siteKey
                ),
                subtitle = viewModel.siteIdOf(siteKey),
                icon = R.drawable.ic_create_pencil
            ) { showSiteIdDialog(siteKey) }
        }

        return extras
    }

    private fun showSiteIdDialog(siteKey: String) {
        InputDialog.newInstance(
            title = R.string.watch_site_id,
            default = viewModel.siteIdOf(siteKey),
            hint = R.string.watch_site_id_hint
        ).also { dialog ->
            dialog.onInputDoneListener = { input ->
                viewModel.setSiteId(siteKey, extractSiteId(siteKey, input))
            }
        }.show(childFragmentManager)
    }

    private fun extractSiteId(siteKey: String, input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (!value.startsWith("http")) return value

        val param = WatchSiteResolvers.of(siteKey)?.idParam ?: return value
        return runCatching { value.toUri().getQueryParameter(param) }.getOrNull() ?: value
    }

    private fun titleOf(episode: MediaEpisodeModel): String = when {
        episode.batch -> getString(R.string.watch_all_episodes)
        episode.number == null -> episode.title.orEmpty()
        viewModel.mediaType == MediaType.MANGA.ordinal ->
            getString(R.string.watch_chapter).format(episode.number)

        else -> getString(R.string.watch_episode).format(episode.number)
    }

    private fun MediaEpisodeModel.target(): MediaEpisodeModel? = if (batch) null else this
}

private enum class WatchFilterChip(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    REVERSE(R.string.watch_reverse, R.drawable.ic_list),
    UNWATCHED(R.string.watch_unwatched, R.drawable.ic_eye);

    fun isOn(filter: WatchEpisodeFilter) = when (this) {
        REVERSE -> filter.descending
        UNWATCHED -> filter.unwatchedOnly
    }

    fun toggle(filter: WatchEpisodeFilter) = when (this) {
        REVERSE -> filter.copy(descending = !filter.descending)
        UNWATCHED -> filter.copy(unwatchedOnly = !filter.unwatchedOnly)
    }
}
