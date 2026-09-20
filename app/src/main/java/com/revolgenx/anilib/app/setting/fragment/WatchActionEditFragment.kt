package com.revolgenx.anilib.app.setting.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.pranavpandey.android.dynamic.support.dialog.DynamicDialog
import com.pranavpandey.android.dynamic.support.model.DynamicMenu
import com.pranavpandey.android.dynamic.theme.Theme
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.ui.fragment.BaseToolbarFragment
import com.revolgenx.anilib.databinding.WatchActionEditFragmentBinding
import com.revolgenx.anilib.databinding.WatchActionFilterItemBinding
import com.revolgenx.anilib.databinding.WatchActionParamItemBinding
import com.revolgenx.anilib.media.data.model.MediaEpisodeModel
import com.revolgenx.anilib.media.data.model.MediaTitleModel
import com.revolgenx.anilib.media.data.model.MediaWatchModel
import com.revolgenx.anilib.media.data.watch.WatchAction
import com.revolgenx.anilib.media.data.watch.WatchActionParam
import com.revolgenx.anilib.media.data.watch.WatchActionScope
import com.revolgenx.anilib.media.data.watch.WatchActionStore
import com.revolgenx.anilib.media.data.watch.WatchFilterSpec
import com.revolgenx.anilib.media.data.watch.WatchFilterType
import com.revolgenx.anilib.media.data.watch.WatchInlineEngines
import com.revolgenx.anilib.media.data.watch.WatchSiteSpecs
import com.revolgenx.anilib.media.data.watch.WatchVars
import com.revolgenx.anilib.type.MediaType
import com.revolgenx.anilib.ui.dialog.InputDialog
import com.revolgenx.anilib.ui.view.makeSpinnerAdapter
import com.revolgenx.anilib.ui.view.makeToast
import com.revolgenx.anilib.util.onItemSelected

class WatchActionEditFragment : BaseToolbarFragment<WatchActionEditFragmentBinding>() {

    override val titleRes: Int = R.string.watch_action
    override val toolbarColorType: Int = Theme.ColorType.BACKGROUND
    override val menuRes: Int = R.menu.watch_action_edit_menu
    override val noScrollToolBar: Boolean = true

    private var action = WatchAction()
    private val params = mutableListOf<WatchActionParam>()

    private val scopes = WatchActionScope.values()
    private val mediaTypes = listOf(null, MediaType.ANIME.ordinal, MediaType.MANGA.ordinal)

    companion object {
        private const val WATCH_ACTION_ID_KEY = "WATCH_ACTION_ID_KEY"

        fun newInstance(actionId: String?) = WatchActionEditFragment().also {
            it.arguments = bundleOf(WATCH_ACTION_ID_KEY to actionId)
        }
    }

    override fun bindView(inflater: LayoutInflater, parent: ViewGroup?) =
        WatchActionEditFragmentBinding.inflate(inflater, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionId = arguments?.getString(WATCH_ACTION_ID_KEY)
        action = actionId?.let { WatchActionStore.find(it) } ?: WatchAction()
        params.clear()
        params += action.params

        bindFields()
        bindSpinners()
        renderFilters()
        renderParams()
        updatePreview()
    }

    private fun bindFields() {
        binding.apply {
            watchActionNameEt.setText(action.name)
            watchActionUrlEt.setText(action.baseUrl)
            watchActionPathEt.setText(action.pathTemplate.orEmpty())
            watchActionSiteEt.setText(action.siteKey.orEmpty())

            watchActionDefaultCb.isChecked = action.isDefault
            watchActionEnabledCb.isChecked = action.enabled
            watchActionInlineCb.isChecked = action.inlineResults

            watchActionUrlEt.onChanged { updatePreview() }
            watchActionPathEt.onChanged { updatePreview() }
            watchActionSiteEt.onChanged {
                action = action.copy(siteKey = it.ifBlank { null })
                bindInlineSupport()
                renderFilters()
                updatePreview()
            }

            watchActionAddParamBtn.setOnClickListener {
                params += WatchActionParam()
                renderParams()
            }
        }

        bindInlineSupport()
    }

    private fun bindInlineSupport() {
        val supported = WatchInlineEngines.of(binding.watchActionSiteEt.text.toString().trim()) != null

        binding.watchActionInlineCb.isEnabled = supported
        binding.watchActionInlineNoteTv.visibility = if (supported) View.GONE else View.VISIBLE
        if (!supported) binding.watchActionInlineCb.isChecked = false
    }

    private fun bindSpinners() {
        val scopeLabels = listOf(
            R.string.watch_scope_episode,
            R.string.watch_scope_media,
            R.string.watch_scope_both
        )

        // the spinner order has to match the enum, both are read back by index
        val orderedScopes = listOf(
            WatchActionScope.EPISODE,
            WatchActionScope.MEDIA,
            WatchActionScope.BOTH
        )

        binding.watchActionScopeSpinner.spinnerView.adapter = makeSpinnerAdapter(
            requireContext(),
            scopeLabels.map { DynamicMenu(null, getString(it)) }
        )
        binding.watchActionScopeSpinner.spinnerView.setSelection(
            orderedScopes.indexOf(action.scope).coerceAtLeast(0)
        )
        binding.watchActionScopeSpinner.spinnerView.onItemSelected { position ->
            action = action.copy(scope = orderedScopes.getOrElse(position) { WatchActionScope.BOTH })
        }

        binding.watchActionTypeSpinner.spinnerView.adapter = makeSpinnerAdapter(
            requireContext(),
            listOf(
                getString(R.string.watch_type_any),
                getString(R.string.anime),
                getString(R.string.manga)
            ).map { DynamicMenu(null, it) }
        )
        binding.watchActionTypeSpinner.spinnerView.setSelection(
            mediaTypes.indexOf(action.mediaType).coerceAtLeast(0)
        )
        binding.watchActionTypeSpinner.spinnerView.onItemSelected { position ->
            action = action.copy(mediaType = mediaTypes.getOrNull(position))
        }
    }

    private fun renderFilters() {
        val container = binding.watchActionFilterContainer
        container.removeAllViews()

        val siteKey = binding.watchActionSiteEt.text.toString().trim().ifBlank { null }
        val specs = WatchSiteSpecs.of(siteKey)

        binding.watchActionFilterHeaderTv.visibility =
            if (specs.isEmpty()) View.GONE else View.VISIBLE
        if (specs.isEmpty()) return

        binding.watchActionFilterHeaderTv.text =
            getString(R.string.watch_action_site_filters).format(siteKey.orEmpty())

        specs.forEach { spec ->
            val item = WatchActionFilterItemBinding.inflate(layoutInflater, container, true)
            item.watchFilterLabelTv.text = spec.label
            item.watchFilterValueTv.text = labelOf(spec, valueOf(spec.key))

            item.root.setOnClickListener { chooseFilter(spec) }
            item.watchFilterClearIv.setOnClickListener {
                setParam(spec.key, null)
                renderFilters()
                renderParams()
                updatePreview()
            }
        }
    }

    private fun renderParams() {
        val container = binding.watchActionParamContainer
        container.removeAllViews()

        params.forEachIndexed { index, param ->
            val item = WatchActionParamItemBinding.inflate(layoutInflater, container, true)
            item.watchParamKeyEt.setText(param.key)
            item.watchParamValueEt.setText(param.value)

            item.watchParamKeyEt.onChanged { text ->
                params.getOrNull(index)?.let { params[index] = it.copy(key = text) }
                updatePreview()
            }
            item.watchParamValueEt.onChanged { text ->
                params.getOrNull(index)?.let { params[index] = it.copy(value = text) }
                updatePreview()
            }
            item.watchParamDeleteIv.setOnClickListener {
                if (index !in params.indices) return@setOnClickListener

                params.removeAt(index)
                renderParams()
                renderFilters()
                updatePreview()
            }
        }
    }

    private fun chooseFilter(spec: WatchFilterSpec) {
        when (spec.type) {
            WatchFilterType.TEXT -> InputDialog.newInstance(
                title = R.string.watch_action_value,
                default = valueOf(spec.key)
            ).also { dialog ->
                dialog.onInputDoneListener = { input -> onFilterPicked(spec.key, input) }
            }.show(childFragmentManager)

            WatchFilterType.ENUM -> showOptions(spec, spec.options.map { it.label }) { index ->
                onFilterPicked(spec.key, spec.options.getOrNull(index)?.value)
            }

            WatchFilterType.TRISTATE -> showOptions(
                spec,
                listOf(
                    getString(R.string.watch_filter_all),
                    getString(R.string.watch_filter_yes),
                    getString(R.string.watch_filter_no)
                )
            ) { index ->
                onFilterPicked(spec.key, listOf(null, "1", "0").getOrNull(index))
            }

            WatchFilterType.BOOL -> showOptions(
                spec,
                listOf(getString(R.string.watch_filter_yes), getString(R.string.watch_filter_no))
            ) { index ->
                onFilterPicked(spec.key, listOf("1", "0").getOrNull(index))
            }

            WatchFilterType.MULTI_ENUM -> showMultiOptions(spec)
        }
    }

    private fun showOptions(
        spec: WatchFilterSpec,
        labels: List<String>,
        onPicked: (Int) -> Unit
    ) {
        DynamicDialog.Builder(requireContext())
            .setTitle(spec.label)
            .setItems(labels.toTypedArray()) { dialog, index ->
                onPicked.invoke(index)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMultiOptions(spec: WatchFilterSpec) {
        val selected = valueOf(spec.key).orEmpty().split(",").map { it.trim() }.toSet()
        val checked = spec.options.map { selected.contains(it.value) }.toBooleanArray()

        DynamicDialog.Builder(requireContext())
            .setTitle(spec.label)
            .setMultiChoiceItems(
                spec.options.map { it.label }.toTypedArray(),
                checked
            ) { _, index, isChecked -> checked[index] = isChecked }
            .setPositiveButton(R.string.done) { dialog, _ ->
                val value = spec.options
                    .filterIndexed { index, _ -> checked[index] }
                    .joinToString(",") { it.value }

                onFilterPicked(spec.key, value)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onFilterPicked(key: String, value: String?) {
        setParam(key, value)
        renderFilters()
        renderParams()
        updatePreview()
    }

    private fun valueOf(key: String): String? =
        params.firstOrNull { it.key == key }?.value?.takeIf { it.isNotBlank() }

    private fun labelOf(spec: WatchFilterSpec, value: String?): String {
        val current = value ?: return getString(R.string.watch_filter_none)

        return when (spec.type) {
            WatchFilterType.TRISTATE, WatchFilterType.BOOL -> when (current) {
                "1" -> getString(R.string.watch_filter_yes)
                "0" -> getString(R.string.watch_filter_no)
                else -> current
            }

            WatchFilterType.MULTI_ENUM -> current.split(",").joinToString(", ") { part ->
                spec.options.firstOrNull { it.value == part.trim() }?.label ?: part
            }

            else -> spec.options.firstOrNull { it.value == current }?.label ?: current
        }
    }

    private fun setParam(key: String, value: String?) {
        val index = params.indexOfFirst { it.key == key }

        when {
            value.isNullOrBlank() -> if (index >= 0) params.removeAt(index)
            index >= 0 -> params[index] = params[index].copy(value = value)
            else -> params += WatchActionParam(key, value)
        }
    }

    private fun updatePreview() {
        val preview = collect()
        val sample = MediaWatchModel().also { model ->
            model.mediaId = 1
            model.idMal = 1
            model.type = MediaType.ANIME.ordinal
            model.episodes = 12
            model.seasonYear = 2024
            model.title = MediaTitleModel(
                english = "Sample Anime",
                romaji = "Sample Anime",
                native = "Sample Anime",
                userPreferred = "Sample Anime"
            )
        }

        val episode = MediaEpisodeModel(number = 1)
        binding.watchActionPreviewTv.text =
            preview.buildUrl(WatchVars.build(sample, episode, preview, emptyMap()))
    }

    private fun collect(): WatchAction = action.copy(
        name = binding.watchActionNameEt.text.toString().trim(),
        baseUrl = binding.watchActionUrlEt.text.toString().trim(),
        pathTemplate = binding.watchActionPathEt.text.toString().trim().ifBlank { null },
        siteKey = binding.watchActionSiteEt.text.toString().trim().ifBlank { null },
        params = params.filter { it.key.isNotBlank() },
        inlineResults = binding.watchActionInlineCb.isChecked,
        enabled = binding.watchActionEnabledCb.isChecked,
        isDefault = binding.watchActionDefaultCb.isChecked
    )

    override fun onToolbarMenuSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.watch_action_save_menu -> save()

            R.id.watch_action_delete_menu -> {
                WatchActionStore.delete(action.id)
                popBackStack()
            }

            else -> return false
        }

        return true
    }

    private fun save() {
        val updated = collect()
        if (updated.name.isBlank() || updated.baseUrl.isBlank()) {
            makeToast(R.string.field_is_empty)
            return
        }

        WatchActionStore.upsert(updated)
        popBackStack()
    }

    private fun View.onChanged(callback: (String) -> Unit) {
        (this as? android.widget.EditText)?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                callback.invoke(s?.toString().orEmpty())
            }
        })
    }
}
