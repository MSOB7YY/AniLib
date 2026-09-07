package com.revolgenx.anilib.ui.view.widgets.chip

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.chip.Chip
import com.revolgenx.anilib.R
import com.revolgenx.anilib.app.theme.dynamicAccentColor
import com.revolgenx.anilib.app.theme.dynamicBackgroundColor
import com.revolgenx.anilib.app.theme.dynamicSurfaceColor
import com.revolgenx.anilib.app.theme.dynamicTextColorPrimary
import com.revolgenx.anilib.app.theme.dynamicTintAccentColor

/** Single choice chip for the media list group row, accent filled while checked. */
class MediaListGroupChip : Chip {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)
    constructor(context: Context, attributeSet: AttributeSet?, defStyle: Int) : super(
        context,
        attributeSet,
        defStyle
    ) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())

        // the row sits over the list, so the checked accent is pulled back towards the background
        val checkedColor = ColorUtils.blendARGB(dynamicAccentColor, dynamicBackgroundColor, 0.2f)

        chipBackgroundColor = ColorStateList(
            states,
            intArrayOf(checkedColor, dynamicSurfaceColor)
        )

        val contentColor = ColorStateList(
            states,
            intArrayOf(dynamicTintAccentColor, dynamicTextColorPrimary)
        )
        setTextColor(contentColor)
        chipIconTint = contentColor

        isCloseIconVisible = false
        isCheckedIconVisible = false
        typeface = ResourcesCompat.getFont(context, R.font.rubik_regular)
    }
}
