package com.revolgenx.anilib.media.data.order

import android.content.Context
import com.revolgenx.anilib.R

object FranchiseFormat {

    fun runtime(context: Context, minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        return if (hours > 0) {
            context.getString(R.string.hours_minutes_format).format(hours, safe % 60)
        } else {
            context.getString(R.string.minutes_format).format(safe)
        }
    }
}
