package com.revolgenx.anilib.app.setting.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pranavpandey.android.dynamic.theme.Theme
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.event.OpenSettingEvent
import com.revolgenx.anilib.common.event.SettingEventTypes
import com.revolgenx.anilib.common.event.WatchActionEventMeta
import com.revolgenx.anilib.common.ui.fragment.BaseToolbarFragment
import com.revolgenx.anilib.databinding.WatchActionItemBinding
import com.revolgenx.anilib.databinding.WatchActionSettingFragmentBinding
import com.revolgenx.anilib.media.data.watch.WatchAction
import com.revolgenx.anilib.media.data.watch.WatchActionStore
import com.revolgenx.anilib.media.data.watch.WatchPreference
import com.revolgenx.anilib.ui.dialog.InputDialog
import com.revolgenx.anilib.ui.view.makeToast
import com.revolgenx.anilib.util.shareText
import java.util.Collections
import java.util.UUID

class WatchActionSettingFragment : BaseToolbarFragment<WatchActionSettingFragmentBinding>() {

    override val titleRes: Int = R.string.watch_actions
    override val toolbarColorType: Int = Theme.ColorType.BACKGROUND
    override val menuRes: Int = R.menu.watch_action_setting_menu
    override val noScrollToolBar: Boolean = true

    private val actions = mutableListOf<WatchAction>()
    private val watchAdapter by lazy { WatchActionAdapter() }

    override fun bindView(inflater: LayoutInflater, parent: ViewGroup?) =
        WatchActionSettingFragmentBinding.inflate(inflater, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.watchActionRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.watchActionRecyclerView.adapter = watchAdapter

        ItemTouchHelper(DragCallback()).attachToRecyclerView(binding.watchActionRecyclerView)

        binding.watchPreferredGroupsView.setOnClickListener {
            editPreference(
                R.string.watch_preferred_groups,
                R.string.watch_preferred_groups_hint,
                WatchPreference.preferredGroupsRaw
            ) { WatchPreference.preferredGroupsRaw = it }
        }

        binding.watchResultLanguagesView.setOnClickListener {
            editPreference(
                R.string.watch_result_languages,
                R.string.watch_result_languages_hint,
                WatchPreference.resultLanguagesRaw
            ) { WatchPreference.resultLanguagesRaw = it }
        }

        bindPreferences()
    }

    private fun bindPreferences() {
        binding.watchPreferredGroupsView.subtitle =
            WatchPreference.preferredGroupsRaw.ifBlank { getString(R.string.watch_filter_none) }
        binding.watchResultLanguagesView.subtitle =
            WatchPreference.resultLanguagesRaw.ifBlank { getString(R.string.watch_filter_all) }
    }

    private fun editPreference(
        titleRes: Int,
        hintRes: Int,
        current: String,
        onDone: (String) -> Unit
    ) {
        InputDialog.newInstance(
            title = titleRes,
            default = current,
            hint = hintRes
        ).also { dialog ->
            dialog.onInputDoneListener = { input ->
                onDone.invoke(input.trim())
                bindPreferences()
            }
        }.show(childFragmentManager)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        actions.clear()
        actions += WatchActionStore.all()
        watchAdapter.notifyDataSetChanged()
        binding.watchActionEmptyTv.visibility =
            if (actions.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onToolbarMenuSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.watch_action_add_menu -> openEditor(null)
            R.id.watch_action_import_menu -> importActions()
            R.id.watch_action_export_menu -> shareText(WatchActionStore.export())
            R.id.watch_action_restore_menu -> {
                WatchActionStore.restoreDefaults()
                reload()
            }

            else -> return false
        }

        return true
    }

    private fun importActions() {
        InputDialog.newInstance(
            title = R.string.watch_action_import,
            hint = R.string.watch_action_import_hint
        ).also { dialog ->
            dialog.onInputDoneListener = { input ->
                if (WatchActionStore.import(input)) {
                    makeToast(R.string.watch_action_imported)
                    reload()
                } else {
                    makeToast(R.string.watch_action_import_failed)
                }
            }
        }.show(childFragmentManager)
    }

    private fun openEditor(actionId: String?) {
        OpenSettingEvent(
            SettingEventTypes.WATCH_ACTION_EDIT,
            WatchActionEventMeta(actionId)
        ).postEvent
    }

    private fun persist() {
        WatchActionStore.saveAll(actions.toList())
    }

    private fun summaryOf(action: WatchAction): String {
        val parts = mutableListOf<String>()

        action.baseUrl.substringAfter("://").substringBefore("/")
            .takeIf { it.isNotBlank() }
            ?.let { parts += it }

        if (action.isDefault) parts += getString(R.string.watch_action_default)
        if (action.inlineResults && action.supportsInline) {
            parts += getString(R.string.watch_action_inline)
        }

        return parts.joinToString(" · ")
    }

    private inner class WatchActionAdapter : RecyclerView.Adapter<WatchActionHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = WatchActionHolder(
            WatchActionItemBinding.inflate(layoutInflater, parent, false)
        )

        override fun getItemCount(): Int = actions.size

        override fun onBindViewHolder(holder: WatchActionHolder, position: Int) {
            holder.bind(actions[position])
        }
    }

    private inner class WatchActionHolder(private val itemBinding: WatchActionItemBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(action: WatchAction) {
            itemBinding.watchActionNameTv.text = action.name
            itemBinding.watchActionSummaryTv.text = summaryOf(action)

            itemBinding.watchActionEnabledCb.setOnCheckedChangeListener(null)
            itemBinding.watchActionEnabledCb.isChecked = action.enabled
            itemBinding.watchActionEnabledCb.setOnCheckedChangeListener { _, checked ->
                val index = actions.indexOfFirst { it.id == action.id }
                if (index < 0) return@setOnCheckedChangeListener

                actions[index] = actions[index].copy(enabled = checked)
                persist()
            }

            itemBinding.root.setOnClickListener { openEditor(action.id) }
            itemBinding.watchActionMenuIv.setOnClickListener { showMenu(it, action) }
        }

        private fun showMenu(anchor: View, action: WatchAction) {
            PopupMenu(requireContext(), anchor).apply {
                menu.add(0, MENU_EDIT, 0, R.string.edit)
                menu.add(0, MENU_DUPLICATE, 1, R.string.watch_action_duplicate)
                menu.add(0, MENU_DEFAULT, 2, R.string.watch_action_default)
                menu.add(0, MENU_DELETE, 3, R.string.delete)

                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_EDIT -> openEditor(action.id)

                        MENU_DUPLICATE -> {
                            WatchActionStore.upsert(
                                action.copy(
                                    id = UUID.randomUUID().toString(),
                                    name = "${action.name} +",
                                    isDefault = false,
                                    order = actions.size
                                )
                            )
                            reload()
                        }

                        MENU_DEFAULT -> {
                            WatchActionStore.upsert(action.copy(isDefault = true))
                            reload()
                        }

                        MENU_DELETE -> {
                            WatchActionStore.delete(action.id)
                            reload()
                        }
                    }

                    true
                }
            }.show()
        }
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from !in actions.indices || to !in actions.indices) return false

            Collections.swap(actions, from, to)
            watchAdapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            persist()
        }
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_DUPLICATE = 2
        private const val MENU_DEFAULT = 3
        private const val MENU_DELETE = 4
    }
}
