package com.revolgenx.anilib.ui.view

import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.pranavpandey.android.dynamic.support.widget.DynamicImageView
import com.revolgenx.anilib.R
import com.revolgenx.anilib.app.theme.dynamicTextColorPrimary

/**
 * Swaps the increment icon for a spinner while the entry is being saved. The state lives on the
 * model, so a row recycled mid save still comes back spinning.
 */
fun DynamicImageView.setProgressUpdating(updating: Boolean) {
    (drawable as? CircularProgressDrawable)?.stop()

    if (updating) {
        setImageDrawable(CircularProgressDrawable(context).apply {
            setStyle(CircularProgressDrawable.DEFAULT)
            setColorSchemeColors(dynamicTextColorPrimary)
            start()
        })
    } else {
        setImageResource(R.drawable.ic_add)
    }
}
