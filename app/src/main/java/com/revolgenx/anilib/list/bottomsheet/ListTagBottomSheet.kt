package com.revolgenx.anilib.list.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.ui.bottomsheet.DynamicBottomSheetFragment
import com.revolgenx.anilib.databinding.ListTagBottomSheetLayoutBinding
import com.revolgenx.anilib.list.data.tag.ListTags
import com.revolgenx.anilib.ui.dialog.InputDialog

class ListTagBottomSheet : DynamicBottomSheetFragment<ListTagBottomSheetLayoutBinding>() {

    override val titleTextRes: Int = R.string.tags

    var onDone: ((tags: List<String>) -> Unit)? = null

    companion object {
        private const val KNOWN_TAGS_KEY = "KNOWN_TAGS_KEY"
        private const val SELECTED_TAGS_KEY = "SELECTED_TAGS_KEY"

        fun newInstance(
            knownTags: List<String>,
            selectedTags: List<String>,
            onDone: (tags: List<String>) -> Unit
        ) = ListTagBottomSheet().also {
            it.arguments = bundleOf(
                KNOWN_TAGS_KEY to ArrayList(knownTags),
                SELECTED_TAGS_KEY to ArrayList(selectedTags)
            )
            it.onDone = onDone
        }
    }

    override fun bindView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ListTagBottomSheetLayoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val known = arguments?.getStringArrayList(KNOWN_TAGS_KEY).orEmpty()
        val selected = arguments?.getStringArrayList(SELECTED_TAGS_KEY).orEmpty()

        binding.listTagChipGroup.onAddTag = { askNewTag() }
        binding.listTagChipGroup.setTags(known, selected)

        onPositiveClicked = {
            onDone?.invoke(binding.listTagChipGroup.getSelectedTags())
            dismiss()
        }
        onNegativeClicked = { dismiss() }
    }

    private fun askNewTag() {
        InputDialog.newInstance(title = R.string.list_tag_add, hint = R.string.list_tag_hint)
            .also { dialog ->
                dialog.onInputDoneListener = { input ->
                    ListTags.normalize(input)?.let { binding.listTagChipGroup.addTag(it) }
                }
            }
            .show(childFragmentManager)
    }
}
