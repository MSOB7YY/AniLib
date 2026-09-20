package com.revolgenx.anilib.media.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Presenter
import com.google.android.material.tabs.TabLayout
import com.otaliastudios.elements.pagers.NoPagesPager
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.preference.canShowAdult
import com.revolgenx.anilib.common.preference.loggedIn
import com.revolgenx.anilib.common.ui.fragment.BaseLayoutFragment
import com.revolgenx.anilib.databinding.MediaChronologyFragmentBinding
import com.revolgenx.anilib.media.data.meta.MediaInfoMeta
import com.revolgenx.anilib.media.data.model.FranchiseTruncation
import com.revolgenx.anilib.media.data.order.FranchiseFilter
import com.revolgenx.anilib.media.data.order.FranchiseFormat
import com.revolgenx.anilib.media.data.order.FranchiseOrderMode
import com.revolgenx.anilib.media.data.order.FranchiseSummary
import com.revolgenx.anilib.media.presenter.MediaChronologyPresenter
import com.revolgenx.anilib.media.viewmodel.MediaChronologyVM
import com.revolgenx.anilib.type.MediaType
import com.revolgenx.anilib.ui.view.widgets.chip.MediaListGroupChip
import com.revolgenx.anilib.util.getParcelableCompat
import org.koin.androidx.viewmodel.ext.android.viewModel

class MediaChronologyFragment : BaseLayoutFragment<MediaChronologyFragmentBinding>() {

    override val titleRes: Int = R.string.chronology
    override val setHomeAsUp: Boolean = true

    private val viewModel by viewModel<MediaChronologyVM>()
    private val orderModes = FranchiseOrderMode.values()

    private val chronologyPresenter by lazy { MediaChronologyPresenter(requireContext()) }

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

        fun newInstance(meta: MediaInfoMeta) = MediaChronologyFragment().also {
            it.arguments = bundleOf(MEDIA_INFO_META_KEY to meta)
        }
    }

    override fun bindView(inflater: LayoutInflater, parent: ViewGroup?) =
        MediaChronologyFragmentBinding.inflate(inflater, parent, false)

    override fun getBaseToolbar(): Toolbar = binding.dynamicToolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val meta = arguments?.getParcelableCompat<MediaInfoMeta>(MEDIA_INFO_META_KEY) ?: return
        val mediaId = meta.mediaId ?: return

        viewModel.field.rootMediaId = mediaId
        viewModel.field.rootMediaType = meta.type ?: MediaType.ANIME.ordinal
        viewModel.filter = viewModel.filter.copy(showAdult = canShowAdult())

        binding.chronologyRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewModel.sourceLiveData.observe(viewLifecycleOwner) { source ->
            binding.chronologySwipeToRefresh.isRefreshing = false
            Adapter.builder(this)
                .setPager(NoPagesPager())
                .addSource(source)
                .addPresenter(chronologyPresenter)
                .addPresenter(loadingPresenter)
                .addPresenter(errorPresenter)
                .addPresenter(emptyPresenter)
                .into(binding.chronologyRecyclerView)
        }

        viewModel.summaryLiveData.observe(viewLifecycleOwner) { bindSummary(it) }

        binding.chronologySwipeToRefresh.setOnRefreshListener {
            binding.chronologySwipeToRefresh.isRefreshing = false
            viewModel.load(force = true)
        }
        binding.chronologyLoadMoreTv.setOnClickListener { viewModel.loadMore() }

        setupOrderTabs()
        renderFilterChips()

        viewModel.load()
        // a recreated view needs its own source, the loaded graph is only re-sorted
        viewModel.rebuild()
    }

    private fun bindSummary(summary: FranchiseSummary?) {
        binding.apply {
            if (summary == null || summary.entries == 0) {
                chronologySummaryCard.visibility = View.GONE
                chronologyNoticeView.visibility = View.GONE
                return
            }

            chronologySummaryCard.visibility = View.VISIBLE
            chronologyEntriesValueTv.text = summary.entries.toString()

            val isAnime = viewModel.field.rootMediaType == MediaType.ANIME.ordinal
            chronologyEpisodesLabelTv.setText(if (isAnime) R.string.episodes else R.string.chapters)
            chronologyEpisodesValueTv.text =
                (if (isAnime) summary.episodes else summary.chapters).toString()
            chronologyRuntimeValueTv.text =
                FranchiseFormat.runtime(requireContext(), summary.minutes)

            val notice = when (summary.truncation) {
                null -> null
                FranchiseTruncation.PARTIAL_FAILURE -> getString(R.string.franchise_partial)
                FranchiseTruncation.MAX_MEDIA ->
                    getString(R.string.franchise_truncated).format(summary.entries)
                else -> getString(R.string.franchise_more_available)
            }

            chronologyNoticeView.visibility = if (notice == null) View.GONE else View.VISIBLE
            chronologyNoticeTv.text = notice.orEmpty()
            chronologyLoadMoreTv.visibility =
                if (summary.canLoadMore) View.VISIBLE else View.GONE
        }
    }

    private fun setupOrderTabs() {
        val tabLayout = binding.chronologyOrderTabLayout
        tabLayout.getTabAt(viewModel.orderMode.ordinal)?.select()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val mode = orderModes.getOrNull(tab.position) ?: return
                if (viewModel.orderMode == mode) return
                viewModel.orderMode = mode
                viewModel.rebuild()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun renderFilterChips() {
        val group = binding.chronologyFilterChipGroup

        FranchiseFilterChip.values().forEach { chipType ->
            if (chipType == FranchiseFilterChip.ON_MY_LIST && !loggedIn()) return@forEach

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
        binding.chronologyFilterChipGroup.children.forEach { chip ->
            val chipType = chip.tag as? FranchiseFilterChip ?: return@forEach
            (chip as MediaListGroupChip).isChecked = chipType.isOn(viewModel.filter)
        }
    }
}

private enum class FranchiseFilterChip(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    SIDE_STORIES(R.string.side_stories, R.drawable.ic_media),
    SPECIALS(R.string.specials, R.drawable.ic_star),
    ON_MY_LIST(R.string.on_my_list, R.drawable.ic_list);

    fun isOn(filter: FranchiseFilter) = when (this) {
        SIDE_STORIES -> filter.includeSideStories
        SPECIALS -> filter.includeSpecials
        ON_MY_LIST -> filter.onMyListOnly
    }

    fun toggle(filter: FranchiseFilter) = when (this) {
        SIDE_STORIES -> filter.copy(includeSideStories = !filter.includeSideStories)
        SPECIALS -> filter.copy(includeSpecials = !filter.includeSpecials)
        ON_MY_LIST -> filter.copy(onMyListOnly = !filter.onMyListOnly)
    }
}
