package com.revolgenx.anilib.home.list.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.tabs.TabLayoutMediator
import com.revolgenx.anilib.R
import com.revolgenx.anilib.activity.viewmodel.MainSharedVM
import com.revolgenx.anilib.app.theme.dynamicBackgroundColor
import com.revolgenx.anilib.common.preference.loggedIn
import com.revolgenx.anilib.common.ui.adapter.makeViewPagerAdapter2
import com.revolgenx.anilib.common.ui.adapter.registerOnPageChangeCallback
import com.revolgenx.anilib.common.ui.adapter.setupWithViewPager2
import com.revolgenx.anilib.common.ui.fragment.BaseLayoutFragment
import com.revolgenx.anilib.databinding.MediaListCollectionContainerFragmentBinding
import com.revolgenx.anilib.common.event.OpenNotificationCenterEvent
import com.revolgenx.anilib.common.preference.UserPreference
import com.revolgenx.anilib.list.fragment.AnimeListCollectionFragment
import com.revolgenx.anilib.list.fragment.BaseMediaListCollectionFragment
import com.revolgenx.anilib.list.fragment.MangaListCollectionFragment
import com.revolgenx.anilib.list.viewmodel.MediaListCollectionContainerCallback
import com.revolgenx.anilib.list.viewmodel.MediaListContainerSharedVM
import com.revolgenx.anilib.list.viewmodel.MediaListGroupState
import com.revolgenx.anilib.list.viewmodel.MediaListScroller
import com.revolgenx.anilib.ui.view.widgets.chip.MediaListGroupChip
import com.revolgenx.anilib.notification.viewmodel.NotificationStoreViewModel
import com.revolgenx.anilib.type.MediaType
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MediaListCollectionContainerFragment :
    BaseLayoutFragment<MediaListCollectionContainerFragmentBinding>() {
    override val menuRes: Int = R.menu.media_list_collection_container_menu

    private val fragments: List<BaseMediaListCollectionFragment> by lazy {
        listOf(
            AnimeListCollectionFragment(),
            MangaListCollectionFragment()
        )
    }
    private val sharedViewModel by viewModel<MediaListContainerSharedVM>()
    private val mainSharedVM by sharedViewModel<MainSharedVM>()
    private val notificationStoreVM by sharedViewModel<NotificationStoreViewModel>()

    private val badgeDrawable by lazy {
        BadgeDrawable.create(requireContext())
    }

    /** Groups the chip row is currently showing, so a re-render only rebuilds when they change. */
    private var renderedGroups: List<Pair<String, Int>>? = null

    private val currentMediaType
        get() = if (binding.alListViewPager.currentItem == 0) MediaType.ANIME else MediaType.MANGA

    /** The scroller belonging to whichever list page the pager is currently showing. */
    private val currentScroller: MediaListScroller?
        get() = when (binding.alListViewPager.currentItem) {
            0 -> sharedViewModel.animeListScroller
            else -> sharedViewModel.mangaListScroller
        }

    override fun bindView(
        inflater: LayoutInflater,
        parent: ViewGroup?
    ): MediaListCollectionContainerFragmentBinding {
        return MediaListCollectionContainerFragmentBinding.inflate(inflater, parent, false)
    }

    override fun getBaseToolbar(): Toolbar {
        return binding.dynamicToolbar
    }


    @SuppressLint("UnsafeOptInUsageError")
    override fun onToolbarInflated() {
        val notificationMenuItem = getBaseToolbar().menu.findItem(R.id.list_notification_menu)
        if (loggedIn()) {
            notificationStoreVM.unreadNotificationCount.observe(viewLifecycleOwner) {
                if (it > 0) {
                    BadgeUtils.attachBadgeDrawable(
                        badgeDrawable,
                        getBaseToolbar(),
                        R.id.list_notification_menu
                    )
                } else {
                    BadgeUtils.detachBadgeDrawable(
                        badgeDrawable,
                        getBaseToolbar(),
                        R.id.list_notification_menu
                    )
                }
            }
        } else {
            notificationMenuItem.isVisible = false
        }
    }

    override fun onToolbarMenuSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.list_search_menu -> {
                sharedViewModel.mediaListContainerCallback.value =
                    MediaListCollectionContainerCallback.SEARCH to binding.alListViewPager.currentItem
                true
            }
            R.id.list_notification_menu -> {
                notificationStoreVM.setUnreadNotificationCount(0)
                OpenNotificationCenterEvent().postEvent
                true
            }
            R.id.list_display_mode_menu -> {
                sharedViewModel.mediaListContainerCallback.value =
                    MediaListCollectionContainerCallback.DISPLAY to binding.alListViewPager.currentItem
                true
            }
            R.id.list_filter_menu -> {
                sharedViewModel.mediaListContainerCallback.value =
                    MediaListCollectionContainerCallback.FILTER to binding.alListViewPager.currentItem
                true
            }
            else -> super.onToolbarMenuSelected(item)
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel.userId = UserPreference.userId
        binding.apply {
            alListViewPager.adapter = makeViewPagerAdapter2(fragments)
            setupWithViewPager2(
                listTabLayout,
                alListViewPager,
                resources.getStringArray(R.array.list_tab_menu)
            )
            registerOnPageChangeCallback(alListViewPager, object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    sharedViewModel.mediaListContainerCallback.value =
                        MediaListCollectionContainerCallback.CURRENT_TAB to if (position == 0)
                            MediaType.ANIME.ordinal
                        else MediaType.MANGA.ordinal
                    renderGroups(sharedViewModel.groupState(currentMediaType).value)
                }
            })

            listGroupScrollView.setBackgroundColor(dynamicBackgroundColor)

            listOf(MediaType.ANIME, MediaType.MANGA).forEach { mediaType ->
                sharedViewModel.groupState(mediaType).observe(viewLifecycleOwner) {
                    if (mediaType != currentMediaType) return@observe
                    renderGroups(it)
                }
            }

            mainSharedVM.mediaListCurrentTab.observe(viewLifecycleOwner) {
                when (it) {
                    MediaType.ANIME.ordinal -> {
                        alListViewPager.post {
                            alListViewPager.currentItem = 0
                        }
                    }
                    MediaType.MANGA.ordinal -> {
                        alListViewPager.post {
                            alListViewPager.currentItem = 1
                        }
                    }
                    else -> {
                        return@observe
                    }
                }
                mainSharedVM.mediaListCurrentTab.value = null
            }

            // re-tapping the list tab toggles: it goes to the top, but when already there it
            // goes to the bottom instead, so one button covers both ends of a long list
            mainSharedVM.listNavigateToTopListener = goToTop@{
                context ?: return@goToTop
                val scroller = currentScroller ?: return@goToTop
                if (scroller.isAtTop()) {
                    scroller.scrollToBottom()
                } else {
                    scroller.scrollToTop()
                }
            }
        }
    }

    /**
     * Rebuilds the chip row only when the groups themselves changed, so simply picking another
     * group just moves the checked state instead of inflating every chip again.
     */
    private fun renderGroups(state: MediaListGroupState?) {
        val groups = state?.groups.orEmpty()
        binding.listGroupScrollView.visibility =
            if (groups.isEmpty()) View.GONE else View.VISIBLE

        val chipGroup = binding.listGroupChipGroup

        if (groups != renderedGroups) {
            renderedGroups = groups
            chipGroup.removeAllViews()

            val inflater = LayoutInflater.from(requireContext())
            groups.forEach { (name, count) ->
                val chip = inflater.inflate(
                    R.layout.media_list_group_chip,
                    chipGroup,
                    false
                ) as MediaListGroupChip

                val icon = groupIconOf(name)
                chip.tag = name
                chip.text = if (icon == null) "%s %d".format(name, count) else count.toString()
                chip.chipIcon = icon?.let { AppCompatResources.getDrawable(requireContext(), it) }
                chip.setOnClickListener {
                    sharedViewModel.groupSelection.value = name to currentMediaType.ordinal
                }

                chipGroup.addView(chip)
            }
        }

        chipGroup.children.forEach { chip ->
            (chip as MediaListGroupChip).isChecked = chip.tag == state?.selected
        }
    }

    /** Default lists get an icon so the row stays short, custom lists keep their name. */
    @DrawableRes
    private fun groupIconOf(name: String): Int? = when (name.lowercase()) {
        "all" -> R.drawable.ic_list
        "watching", "reading" -> R.drawable.ic_watching
        "planning" -> R.drawable.ic_planning
        "completed" -> R.drawable.ic_completed
        "paused" -> R.drawable.ic_paused_filled
        "dropped" -> R.drawable.ic_dropped
        "rewatching", "rereading", "repeating" -> R.drawable.ic_rewatching
        else -> null
    }

    override fun onDestroyView() {
        // the chips belong to the view that is going away, so a theme change rebuilds them rather
        // than leaving the row empty because the groups themselves did not change
        renderedGroups = null
        super.onDestroyView()
    }

}