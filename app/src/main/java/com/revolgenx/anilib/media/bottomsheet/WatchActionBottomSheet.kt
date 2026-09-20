package com.revolgenx.anilib.media.bottomsheet

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.revolgenx.anilib.R
import com.revolgenx.anilib.app.theme.contrastAccentWithSurface
import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.common.ui.bottomsheet.BottomSheetFragment
import com.revolgenx.anilib.databinding.MediaWatchActionItemBinding
import com.revolgenx.anilib.databinding.MediaWatchActionSheetBinding
import com.revolgenx.anilib.databinding.MediaWatchTorrentItemBinding
import com.revolgenx.anilib.media.data.watch.WatchAction
import com.revolgenx.anilib.media.data.watch.WatchPreference
import com.revolgenx.anilib.media.data.watch.WatchResultSort
import com.revolgenx.anilib.media.data.watch.WatchTorrentModel
import com.revolgenx.anilib.ui.view.widgets.chip.MediaListGroupChip
import com.revolgenx.anilib.util.prettyTime

data class WatchSheetExtra(
    val title: String,
    val subtitle: String? = null,
    @DrawableRes val icon: Int = R.drawable.ic_link,
    val action: () -> Unit
)

/** Long press and the tile menu both land here, an inline action swaps the sheet to its results. */
class WatchActionBottomSheet : BottomSheetFragment<MediaWatchActionSheetBinding>() {

    var headerTitle: String? = null
    var actions: List<WatchAction> = emptyList()
    var extras: List<WatchSheetExtra> = emptyList()
    var initialAction: WatchAction? = null

    var urlOf: ((WatchAction) -> String?)? = null
    var onOpenLink: ((String) -> Unit)? = null
    var onSearch: ((WatchAction, (Resource<List<WatchTorrentModel>>) -> Unit) -> Unit)? = null
    var onCopyLink: ((String) -> Unit)? = null

    private var results: List<WatchTorrentModel> = emptyList()
    private var query: String = ""
    private var sort: WatchResultSort = WatchResultSort.SEEDERS
    private var resultAction: WatchAction? = null

    private val visibleResults = mutableListOf<WatchTorrentModel>()
    private val resultAdapter by lazy { TorrentAdapter() }

    private val seederColor by lazy {
        ContextCompat.getColor(requireContext(), R.color.watch_seeder_color)
    }

    private val leecherColor by lazy {
        ContextCompat.getColor(requireContext(), R.color.watch_leecher_color)
    }

    override fun bindView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): MediaWatchActionSheetBinding =
        MediaWatchActionSheetBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.watchSheetTitleTv.text = headerTitle.orEmpty()

        binding.watchSheetResultRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.watchSheetResultRecyclerView.adapter = resultAdapter

        binding.watchSheetSearchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim()
                applyResults()
            }
        })

        renderSortChips()

        val action = initialAction
        if (action != null) runAction(action) else showActions()
    }

    private fun showActions() {
        val container = binding.watchSheetContainer
        container.removeAllViews()

        binding.watchSheetFooterContainer.removeAllViews()
        binding.watchSheetFilterLayout.visibility = View.GONE
        binding.watchSheetActionScrollView.visibility = View.VISIBLE
        binding.watchSheetTitleTv.text = headerTitle.orEmpty()
        submitResults(emptyList())
        setStatus(null)
        setSheetExpanded(false)

        actions.forEach { action ->
            val inline = action.inlineResults && action.supportsInline

            addItem(
                container = container,
                title = action.name,
                subtitle = if (inline) {
                    getString(R.string.watch_inline_results)
                } else {
                    urlOf?.invoke(action)
                },
                icon = if (inline) R.drawable.ic_search else R.drawable.ic_open_window
            ) { runAction(action) }
        }

        extras.forEach { extra ->
            addItem(container, extra.title, extra.subtitle, extra.icon) {
                extra.action.invoke()
                dismiss()
            }
        }
    }

    private fun runAction(action: WatchAction) {
        if (action.inlineResults && action.supportsInline && onSearch != null) {
            search(action)
            return
        }

        urlOf?.invoke(action)?.let { onOpenLink?.invoke(it) }
        dismiss()
    }

    private fun search(action: WatchAction) {
        resultAction = action
        results = emptyList()

        binding.watchSheetContainer.removeAllViews()
        binding.watchSheetFooterContainer.removeAllViews()
        binding.watchSheetActionScrollView.visibility = View.GONE
        binding.watchSheetTitleTv.text =
            getString(R.string.str_dot_str).format(headerTitle.orEmpty(), action.name)

        // results keep the sheet at a fixed height, filtering must not make it jump around
        setSheetExpanded(true)

        onSearch?.invoke(action) { resource ->
            if (!isAdded) return@invoke

            when (resource) {
                is Resource.Loading -> setStatus(getString(R.string.loading))

                is Resource.Error -> setStatus(getString(R.string.something_went_wrong))

                is Resource.Success -> {
                    results = resource.data.orEmpty()
                    binding.watchSheetFilterLayout.visibility =
                        if (results.isEmpty()) View.GONE else View.VISIBLE

                    applyResults()
                    renderFooter(action)
                }
            }
        }
    }

    /** Only the list is recomputed while typing or sorting, the rest of the sheet stays put. */
    private fun applyResults() {
        if (resultAction == null) return

        val filtered = results
            .filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
            .sortedWith(comparatorOf(sort))

        submitResults(filtered)
        setStatus(
            if (filtered.isEmpty()) {
                getString(R.string.watch_no_results)
            } else {
                getString(R.string.watch_result_count).format(filtered.size)
            }
        )
    }

    private fun submitResults(list: List<WatchTorrentModel>) {
        visibleResults.clear()
        visibleResults += list
        resultAdapter.notifyDataSetChanged()
    }

    private fun renderFooter(action: WatchAction) {
        val footer = binding.watchSheetFooterContainer
        footer.removeAllViews()

        urlOf?.invoke(action)?.let { url ->
            addItem(
                footer,
                getString(R.string.watch_open_in_browser),
                null,
                R.drawable.ic_open_window
            ) {
                onOpenLink?.invoke(url)
                dismiss()
            }
        }

        addItem(footer, getString(R.string.watch_actions), null, R.drawable.ic_list) {
            resultAction = null
            showActions()
        }
    }

    private fun comparatorOf(sort: WatchResultSort): Comparator<WatchTorrentModel> {
        // a preferred group always floats up, whatever the user sorts by
        val preferred = compareByDescending<WatchTorrentModel> { it.preferred }

        return when (sort) {
            WatchResultSort.SEEDERS -> preferred.thenByDescending { it.seeders }
            WatchResultSort.DATE -> preferred.thenByDescending { it.uploadedAt }
            WatchResultSort.SIZE -> preferred.thenByDescending { it.size }
            WatchResultSort.TITLE -> preferred.thenBy { it.title.lowercase() }
        }
    }

    private fun renderSortChips() {
        val group = binding.watchSheetSortChipGroup

        WatchResultSort.values().forEach { sortType ->
            val chip = layoutInflater.inflate(
                R.layout.media_watch_sort_chip,
                group,
                false
            ) as MediaListGroupChip

            chip.setText(sortType.labelRes)
            chip.isChecked = sortType == sort
            chip.setOnClickListener {
                sort = sortType
                group.chips().forEach { child -> child.isChecked = child === chip }
                applyResults()
            }

            group.addView(chip)
        }
    }

    private fun setSheetExpanded(expanded: Boolean) {
        val root = binding.watchSheetRoot
        val height = if (expanded) {
            (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }

        val params = root.layoutParams
            ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

        if (params.height == height) return

        params.height = height
        root.layoutParams = params
    }

    private fun setStatus(text: String?) {
        binding.watchSheetStatusTv.visibility = if (text == null) View.GONE else View.VISIBLE
        binding.watchSheetStatusTv.text = text.orEmpty()
    }

    private fun addItem(
        container: ViewGroup,
        title: String,
        subtitle: String?,
        @DrawableRes icon: Int,
        onClick: () -> Unit
    ) {
        val item = MediaWatchActionItemBinding.inflate(layoutInflater, container, true)

        item.watchActionItemIv.setImageResource(icon)
        item.watchActionItemTitleTv.text = title
        item.watchActionItemSubtitleTv.visibility =
            if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        item.watchActionItemSubtitleTv.text = subtitle.orEmpty()
        item.root.setOnClickListener { onClick.invoke() }
    }

    private fun metaOf(torrent: WatchTorrentModel): String {
        val parts = mutableListOf<String>()

        torrent.quality?.let { parts += it }
        languagesOf(torrent)?.let { parts += it }
        if (torrent.hardsub) parts += HARDSUB_LABEL
        if (torrent.batch) parts += getString(R.string.watch_batch)
        if (torrent.uploadedAt > 0) parts += (torrent.uploadedAt / 1000).prettyTime()

        return parts.joinToString(" · ")
    }

    private fun languagesOf(torrent: WatchTorrentModel): String? {
        val wanted = WatchPreference.resultLanguages
        val audio = torrent.audioLanguages.preferredFirst(wanted)
        val subs = torrent.subLanguages.preferredFirst(wanted)

        val parts = mutableListOf<String>()
        if (audio.isNotEmpty()) parts += getString(R.string.watch_audio_langs).format(audio)
        if (subs.isNotEmpty()) parts += getString(R.string.watch_sub_langs).format(subs)

        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Nothing is hidden, the preferred codes are only pulled to the front and the rest counted. */
    private fun List<String>.preferredFirst(wanted: Set<String>): String {
        if (isEmpty()) return ""
        if (wanted.isEmpty()) return joinToString(",").take(LANGUAGE_LABEL_LIMIT)

        val (preferred, rest) = partition { wanted.contains(it.lowercase()) }
        val shown = preferred.ifEmpty { rest.take(1) }
        val hidden = size - shown.size

        return if (hidden > 0) "${shown.joinToString(",")} +$hidden" else shown.joinToString(",")
    }

    private fun ViewGroup.chips(): List<MediaListGroupChip> =
        (0 until childCount).mapNotNull { getChildAt(it) as? MediaListGroupChip }

    private inner class TorrentAdapter : RecyclerView.Adapter<TorrentHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TorrentHolder(
            MediaWatchTorrentItemBinding.inflate(layoutInflater, parent, false)
        )

        override fun getItemCount(): Int = visibleResults.size

        override fun onBindViewHolder(holder: TorrentHolder, position: Int) {
            holder.bind(visibleResults[position])
        }
    }

    private inner class TorrentHolder(private val itemBinding: MediaWatchTorrentItemBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(torrent: WatchTorrentModel) {
            itemBinding.watchTorrentTitleTv.text = torrent.title
            itemBinding.watchTorrentSizeTv.text =
                Formatter.formatShortFileSize(requireContext(), torrent.size)

            itemBinding.watchTorrentSeederTv.text = torrent.seeders.toString()
            itemBinding.watchTorrentSeederTv.setTextColor(seederColor)
            itemBinding.watchTorrentLeecherTv.text = torrent.leechers.toString()
            itemBinding.watchTorrentLeecherTv.setTextColor(leecherColor)

            itemBinding.watchTorrentGroupTv.text = torrent.group.orEmpty()
            itemBinding.watchTorrentMetaTv.text = metaOf(torrent)
            itemBinding.watchTorrentMarkerView.setBackgroundColor(
                if (torrent.preferred) contrastAccentWithSurface else Color.TRANSPARENT
            )

            itemBinding.watchTorrentMagnetIv.setOnClickListener {
                torrent.magnet?.let { magnet -> onOpenLink?.invoke(magnet) }
            }
            itemBinding.watchTorrentDownloadIv.setOnClickListener {
                (torrent.torrentUrl ?: torrent.magnet)?.let { link -> onOpenLink?.invoke(link) }
            }
            itemBinding.root.setOnClickListener {
                (torrent.webUrl ?: torrent.magnet)?.let { link -> onOpenLink?.invoke(link) }
            }
            itemBinding.root.setOnLongClickListener {
                torrent.magnet?.let { magnet -> onCopyLink?.invoke(magnet) }
                true
            }
        }
    }

    companion object {
        private const val HARDSUB_LABEL = "hardsub"
        private const val LANGUAGE_LABEL_LIMIT = 24
        private const val SHEET_HEIGHT_RATIO = 0.85f
    }
}

private val WatchResultSort.labelRes
    get() = when (this) {
        WatchResultSort.SEEDERS -> R.string.watch_sort_seeders
        WatchResultSort.DATE -> R.string.watch_sort_date
        WatchResultSort.SIZE -> R.string.watch_sort_size
        WatchResultSort.TITLE -> R.string.watch_sort_title
    }
