package com.revolgenx.anilib.ui.view.widgets

import android.content.Context
import android.util.AttributeSet
import com.pranavpandey.android.dynamic.support.widget.DynamicRecyclerView
import com.revolgenx.anilib.util.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps the scrollbar usable on long lists. Both the plain scrollbar and the fast scroller size
 * their thumb straight from the content ratio, so a few thousand entries leave a thumb only a
 * couple of pixels tall. Reporting a shortened scroll range caps how small it can get, and the
 * offset is shortened by the same factor so the thumb still tracks the list from end to end.
 */
class AlRecyclerView : DynamicRecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet?, defStyle: Int) : super(
        context,
        attributeSet,
        defStyle
    )

    private val minThumbHeight = dp(48f)

    private val visibleHeight get() = height - paddingTop - paddingBottom

    override fun computeVerticalScrollRange(): Int = cappedRange(super.computeVerticalScrollRange())

    override fun computeVerticalScrollOffset(): Int {
        val offset = super.computeVerticalScrollOffset()
        val range = super.computeVerticalScrollRange()
        val capped = cappedRange(range)
        if (capped == range) return offset

        val visible = visibleHeight
        val scrollable = range - visible
        if (scrollable <= 0) return offset

        return (offset.toLong() * (capped - visible) / scrollable).toInt()
    }

    /** The thumb is `visible * visible / range` tall, so the range decides its smallest size. */
    private fun cappedRange(range: Int): Int {
        val visible = visibleHeight
        if (visible <= 0 || minThumbHeight <= 0) return range
        return min(range, max(visible, visible * visible / minThumbHeight))
    }
}
