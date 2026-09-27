package com.revolgenx.anilib.ui.view.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.pranavpandey.android.dynamic.support.widget.DynamicImageView
import com.pranavpandey.android.dynamic.support.widget.DynamicTextView
import com.pranavpandey.android.dynamic.theme.Theme
import com.pranavpandey.android.dynamic.utils.DynamicUnitUtils
import com.revolgenx.anilib.R
import com.revolgenx.anilib.list.data.tag.ListTags

class ListTagRowView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : LinearLayout(context, attributeSet) {

    private val icons = mutableListOf<DynamicImageView>()
    private val customTv = DynamicTextView(context).also { tv ->
        tv.textSize = 10f
        tv.maxLines = 1
        tv.colorType = Theme.ColorType.TEXT_SECONDARY
    }
    private val placeholderIv = newIcon().also { it.setImageResource(R.drawable.ic_tag); it.alpha = 0.35f }

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(placeholderIv)
        addView(customTv)
    }

    fun setTags(tags: List<String>, showPlaceholder: Boolean) {
        var iconIndex = 0
        val custom = mutableListOf<String>()

        tags.forEach { tag ->
            val icon = ListTags.iconOf(tag)
            if (icon == null) {
                custom += tag
                return@forEach
            }
            val view = icons.getOrNull(iconIndex) ?: newIcon().also {
                icons += it
                addView(it, iconIndex)
            }
            view.setImageResource(icon)
            view.visibility = View.VISIBLE
            iconIndex++
        }
        for (index in iconIndex until icons.size) icons[index].visibility = View.GONE

        customTv.visibility = if (custom.isEmpty()) View.GONE else View.VISIBLE
        if (custom.isNotEmpty()) customTv.text = custom.joinToString(" ") { "#$it" }

        placeholderIv.visibility = if (tags.isEmpty() && showPlaceholder) View.VISIBLE else View.GONE
    }

    private fun newIcon() = DynamicImageView(context).also { iv ->
        val size = DynamicUnitUtils.convertDpToPixels(11f)
        val spacing = DynamicUnitUtils.convertDpToPixels(3f)
        iv.layoutParams = LayoutParams(size, size).also {
            it.marginStart = spacing
            it.marginEnd = spacing
        }
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.colorType = Theme.ColorType.ACCENT
    }
}
