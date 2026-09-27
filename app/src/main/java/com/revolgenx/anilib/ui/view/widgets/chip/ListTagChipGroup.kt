package com.revolgenx.anilib.ui.view.widgets.chip

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.children
import com.google.android.material.chip.ChipGroup
import com.revolgenx.anilib.R
import com.revolgenx.anilib.list.data.tag.ListTags

class ListTagChipGroup @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : ChipGroup(context, attributeSet) {

    var onAddTag: (() -> Unit)? = null

    private val known = linkedSetOf<String>()

    fun getSelectedTags(): List<String> {
        val selected = mutableListOf<String>()
        for (child in children) {
            val chip = child as? MediaListGroupChip ?: continue
            if (!chip.isCheckable || !chip.isChecked) continue
            val tag = chip.tag as? String ?: continue
            selected += tag
        }
        return selected
    }

    fun setTags(knownTags: Collection<String>, selectedTags: Collection<String>) {
        known.clear()
        known += knownTags
        known += selectedTags
        render(selectedTags.toSet())
    }

    fun addTag(tag: String) {
        val selected = getSelectedTags().toMutableSet()
        selected += tag
        known += tag
        render(selected)
    }

    private fun render(selected: Set<String>) {
        removeAllViews()
        val inflater = LayoutInflater.from(context)

        known.forEach { tag ->
            val chip = inflater.inflate(R.layout.media_list_group_chip, this, false) as MediaListGroupChip
            chip.tag = tag
            chip.text = tag
            chip.isClickable = true
            chip.isFocusable = true
            chip.isChecked = tag in selected
            chip.chipIcon = ListTags.iconOf(tag)?.let { AppCompatResources.getDrawable(context, it) }
            addView(chip)
        }

        val onAdd = onAddTag ?: return
        val addChip = inflater.inflate(R.layout.media_list_group_chip, this, false) as MediaListGroupChip
        addChip.isCheckable = false
        addChip.text = context.getString(R.string.list_tag_add)
        addChip.chipIcon = AppCompatResources.getDrawable(context, R.drawable.ic_add)
        addChip.setOnClickListener { onAdd.invoke() }
        addView(addChip)
    }
}
