package com.revolgenx.anilib.list.fragment

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Presenter
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.pagers.NoPagesPager
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.preference.*
import com.revolgenx.anilib.common.repository.network.OfflineCacheState
import com.revolgenx.anilib.common.ui.fragment.BaseLayoutFragment
import com.revolgenx.anilib.common.viewmodel.getViewModelOwner
import com.revolgenx.anilib.constant.MediaListDisplayMode
import com.revolgenx.anilib.databinding.MediaListCollectionFragmentBinding
import com.revolgenx.anilib.list.presenter.MediaListCollectionPresenter
import com.revolgenx.anilib.list.bottomsheet.ListTagBottomSheet
import com.revolgenx.anilib.list.bottomsheet.MediaListCollectionFilterBottomSheet
import com.revolgenx.anilib.list.data.meta.MediaListCollectionFilterMeta
import com.revolgenx.anilib.list.bottomsheet.MediaListDisplaySelectorBottomSheet
import com.revolgenx.anilib.type.MediaType
import com.revolgenx.anilib.list.viewmodel.MediaListCollectionVM
import com.revolgenx.anilib.list.viewmodel.MediaListContainerSharedVM
import com.revolgenx.anilib.list.viewmodel.MediaListScroller
import com.revolgenx.anilib.list.bottomsheet.MediaListGroupSelectorBottomSheet
import com.revolgenx.anilib.list.data.model.MediaListModel
import com.revolgenx.anilib.list.event.ListEvent
import com.revolgenx.anilib.util.EventBusListener
import com.revolgenx.anilib.util.registerForEvent
import com.revolgenx.anilib.util.unRegisterForEvent
import org.greenrobot.eventbus.Subscribe
import com.revolgenx.anilib.list.viewmodel.MediaListCollectionContainerCallback
import com.revolgenx.anilib.list.viewmodel.MediaListCollectionStoreVM
import com.revolgenx.anilib.list.viewmodel.MediaListGroupState
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

abstract class BaseMediaListCollectionFragment() :
    BaseLayoutFragment<MediaListCollectionFragmentBinding>(), EventBusListener {
    abstract val mediaType: MediaType

    protected abstract val listCollectionStoreVM: MediaListCollectionStoreVM
    protected val viewModel by viewModel<MediaListCollectionVM> { parametersOf(listCollectionStoreVM) }

    private val containerSharedVM by viewModel<MediaListContainerSharedVM>(owner = getViewModelOwner())


    protected val isLoggedInUser by lazy { viewModel.field.userId == UserPreference.userId }

    private var adapter: Adapter? = null

    private val basePresenter: MediaListCollectionPresenter
        get() = MediaListCollectionPresenter(
            requireContext(),
            isLoggedInUser,
            mediaType,
            viewModel,
            onEditTags = { item -> openTagPicker(item) }
        )

    override fun onStart() {
        super.onStart()
        registerForEvent()
    }

    override fun onStop() {
        super.onStop()
        unRegisterForEvent()
    }

    @Subscribe
    fun onListEvent(event: ListEvent) {
        when (event) {
            is ListEvent.ListUpdateEvent -> viewModel.onEntryEdited(event.list)
            is ListEvent.ListDeleteEvent -> viewModel.onEntryDeleted(event.id)
            is ListEvent.ListAddEvent -> {}
        }
    }

    private fun openTagPicker(item: MediaListModel) {
        ListTagBottomSheet.newInstance(viewModel.getKnownTags(), item.tags) { tags ->
            if (context == null) return@newInstance
            viewModel.setTags(item, tags)
        }.show(this)
    }

    private val errorPresenter: Presenter<Unit> by lazy {
        Presenter.forErrorIndicator(requireContext(), R.layout.error_layout)
    }

    private val emptyPresenter: Presenter<Unit> by lazy {
        Presenter.forEmptyIndicator(requireContext(), R.layout.empty_layout)
    }

    protected open val loadingPresenter: Presenter<Unit>
        get() = Presenter.forLoadingIndicator(requireContext(), R.layout.loading_layout)

    override fun bindView(
        inflater: LayoutInflater,
        parent: ViewGroup?
    ): MediaListCollectionFragmentBinding =
        MediaListCollectionFragmentBinding.inflate(inflater, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!containerSharedVM.hasUserData) return

        viewModel.type = mediaType
        viewModel.field.userId = containerSharedVM.userId
        viewModel.field.userName = containerSharedVM.userName
        binding.onBind()

        val scroller = object : MediaListScroller {
            override fun isAtTop() = binding.alListRecyclerView.computeVerticalScrollOffset() == 0
            override fun scrollToTop() = binding.alListRecyclerView.scrollToTop()
            override fun scrollToBottom() = binding.alListRecyclerView.scrollToBottom()
        }

        when (mediaType) {
            MediaType.ANIME -> containerSharedVM.animeListScroller = scroller
            MediaType.MANGA -> containerSharedVM.mangaListScroller = scroller
            else -> {}
        }
    }

    private fun RecyclerView.scrollToTop() = scrollToPosition(0)

    private fun RecyclerView.scrollToBottom() {
        val lastIndex = (adapter?.itemCount ?: 0) - 1
        if (lastIndex < 0) return
        scrollToPosition(lastIndex)
    }

    private fun loadLayoutManager() {
        binding.alListRecyclerView.layoutManager = getLayoutManager()
    }

    private fun MediaListCollectionFragmentBinding.onBind() {
        loadLayoutManager()

        viewModel.sourceLiveData.observe(viewLifecycleOwner) {
            alListSwipeToRefresh.isRefreshing = false
            invalidateSource()
        }

        containerSharedVM.mediaListContainerCallback.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            if (it.second == mediaType.ordinal) {
                when (it.first) {
                    MediaListCollectionContainerCallback.SEARCH -> {
                        toggleSearch()
                    }
                    MediaListCollectionContainerCallback.GROUP -> {
                        viewModel.groupNamesWithCount.value
                            ?.toList()
                            ?.map { it to (it.first == viewModel.currentGroupNameHistory) }
                            ?.let {
                                MediaListGroupSelectorBottomSheet.newInstance(it) { selected ->
                                    if (context == null) return@newInstance
                                    selectGroup(selected)
                                }.show(requireContext())
                            }
                    }
                    MediaListCollectionContainerCallback.CURRENT_TAB -> {
                        updateCurrentGroupWithCount()
                    }
                    MediaListCollectionContainerCallback.FILTER -> openFilterSheet()
                    MediaListCollectionContainerCallback.DISPLAY -> {
                        MediaListDisplaySelectorBottomSheet.newInstance(
                            if (isLoggedInUser) {
                                getUserMediaListCollectionDisplayMode(mediaType)
                            } else {
                                getGeneralMediaListCollectionDisplayMode(mediaType)
                            }.ordinal
                        ) {
                            if (context == null) return@newInstance

                            if (isLoggedInUser) {
                                setUserMediaListCollectionDisplayMode(mediaType, it)
                            } else {
                                setGeneralMediaListCollectionDisplayMode(mediaType, it)
                            }
                            loadLayoutManager()
                            invalidateSource()
                        }.show(requireContext())
                    }
                }

                containerSharedVM.mediaListContainerCallback.value = null
            }
        }

        viewModel.groupNamesWithCount.observe(viewLifecycleOwner) {
            updateCurrentGroupWithCount()
        }

        viewModel.hiddenCount.observe(viewLifecycleOwner) { renderFilterInfo(it) }
        alListFilterInfoLayout.setOnClickListener { openFilterSheet() }
        alListFilterClearIv.setOnClickListener {
            if (viewModel.hasGroupFilter) {
                viewModel.clearGroupFilter()
            } else {
                viewModel.applyFilter(MediaListCollectionFilterMeta(), thisGroupOnly = false)
            }
        }

        containerSharedVM.groupSelection.observe(viewLifecycleOwner) {
            if (it == null || it.second != mediaType.ordinal) return@observe
            selectGroup(it.first)
            containerSharedVM.groupSelection.value = null
        }

        OfflineCacheState.servingCachedData.observe(viewLifecycleOwner) {
            alListOfflineView.visibility = if (it) View.VISIBLE else View.GONE
        }

        alListSwipeToRefresh.setOnRefreshListener {
            viewModel.getMediaList()
        }

        alListSearchEt.doOnTextChanged { text, _, _, _ ->
            viewModel.search = text?.toString() ?: ""
        }

        alListSearchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            viewModel.search = alListSearchEt.text?.toString() ?: ""
            true
        }

        alListClearSearchIv.setOnClickListener {
            alListSearchEt.setText("")
        }

        showAlListSearchView()
    }

    private fun openFilterSheet() {
        val group = viewModel.currentGroupNameHistory
        MediaListCollectionFilterBottomSheet.newInstance(
            viewModel.activeFilter.copy(),
            groupName = group.takeIf { it != "All" },
            thisGroupOnly = viewModel.hasGroupFilter,
            knownTags = viewModel.getKnownTags()
        ) { filter, thisGroupOnly ->
            if (context == null) return@newInstance
            viewModel.applyFilter(filter, thisGroupOnly)
        }.show(requireContext())
    }

    private fun renderFilterInfo(hidden: Int) {
        val filter = viewModel.activeFilter
        val ownFilter = viewModel.hasGroupFilter
        val visible = ownFilter || (hidden > 0 && filter.hidesAnything)
        binding.alListFilterInfoLayout.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        val parts = mutableListOf<String>()
        if (ownFilter) parts += getString(R.string.list_filter_own)
        if (filter.hideNotYetReleased) parts += getString(R.string.list_filter_hiding_unreleased)
        if (filter.hideWatchedSequels) parts += getString(R.string.list_filter_hiding_sequels)
        filter.tags?.takeIf { it.isNotEmpty() }?.let { parts += it.joinToString(" ") { tag -> "#$tag" } }
        parts += getString(R.string.list_filter_hidden_count).format(hidden)
        binding.alListFilterInfoTv.text = parts.joinToString(" · ")
    }

    private fun invalidateSource() {
        val source = viewModel.sourceLiveData.value ?: return
        invalidateAdapter(basePresenter, source)
    }

    private fun updateCurrentGroupWithCount() {
        val groupNamesWithCount = viewModel.groupNamesWithCount.value
        val currentGroupName = viewModel.currentGroupNameHistory

        containerSharedVM.currentGroupNameWithCount.value = groupNamesWithCount
            ?.get(currentGroupName)
            ?.let { currentGroupName!! to it }

        containerSharedVM.groupState(mediaType).value = groupNamesWithCount?.let {
            MediaListGroupState(it.toList(), currentGroupName, viewModel.groupsWithOwnFilter)
        }
    }

    private fun selectGroup(groupName: String) {
        if (groupName == viewModel.currentGroupNameHistory) return

        if (isLoggedInUser) {
            if (mediaType == MediaType.ANIME) {
                animeListStatusHistory(groupName)
            } else {
                mangaListStatusHistory(groupName)
            }
        } else {
            viewModel.groupNameHistory = groupName
        }
        updateCurrentGroupWithCount()
        viewModel.filter()
        renderFilterInfo(0)
    }


    private fun showAlListSearchView() {
        binding.alListSearchLayout.visibility =
            if (viewModel.searchViewVisible) View.VISIBLE else View.GONE
    }

    private fun MediaListCollectionFragmentBinding.showError() {
        errorLayout.errorLayout.visibility = View.VISIBLE
        emptyLayout.emptyLayout.visibility = View.GONE
    }

    private fun MediaListCollectionFragmentBinding.showEmpty() {
        emptyLayout.emptyLayout.visibility = View.VISIBLE
        errorLayout.errorLayout.visibility = View.GONE
    }


    private fun getLayoutManager(): GridLayoutManager {
        var span =
            if (requireContext().resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        when (if (isLoggedInUser) getUserMediaListCollectionDisplayMode(mediaType) else getGeneralMediaListCollectionDisplayMode(
            mediaType
        )) {
            MediaListDisplayMode.NORMAL, MediaListDisplayMode.MINIMAL_LIST -> span /= 2
            MediaListDisplayMode.CLASSIC, MediaListDisplayMode.MINIMAL -> span += 1
            else -> {
            }
        }
        return GridLayoutManager(this.context, span).also {
            it.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter?.getItemViewType(position) == 0) {
                        1
                    } else {
                        span
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!visibleToUser) {
            viewModel.getMediaList()
        }
        visibleToUser = true
    }


    protected fun notifyDataSetChanged() {
        adapter?.notifyDataSetChanged()
    }

    private fun invalidateAdapter(
        presenter: MediaListCollectionPresenter,
        source: Source<MediaListModel>
    ) {
        adapter =
            Adapter.builder(this)
                .setPager(NoPagesPager())
                .addSource(source)
                .addPresenter(presenter)
                .addPresenter(emptyPresenter)
                .addPresenter(errorPresenter)
                .addPresenter(loadingPresenter)
                .into(binding.alListRecyclerView)
    }

    private fun toggleSearch() {
        viewModel.searchViewVisible = !viewModel.searchViewVisible
        showAlListSearchView()
    }

}