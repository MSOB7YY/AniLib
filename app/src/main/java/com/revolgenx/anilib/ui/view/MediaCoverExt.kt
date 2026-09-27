package com.revolgenx.anilib.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.image.ImageInfo
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.preference.malCoversEnabled
import com.revolgenx.anilib.media.data.cover.MalCovers
import com.revolgenx.anilib.media.data.model.MediaCoverImageModel
import java.lang.ref.WeakReference

fun SimpleDraweeView.setCover(cover: MediaCoverImageModel?, large: Boolean = false) {
    val oldBinding = getTag(R.id.malCoverBinding) as MalCoverBinding?
    oldBinding?.hideLoading(this)

    val malImage = cover?.malImage(large)
    val isMalCover = malImage != null || (cover != null && cover.malKey != 0 && malCoversEnabled())
    if (cover == null || !isMalCover) {
        setTag(R.id.malCoverBinding, null)
        val image = cover?.aniListImage(large)
        setImageURI(image)
        return
    }

    val binding = MalCoverBinding(this, cover, large)
    setTag(R.id.malCoverBinding, binding)
    binding.show(this, malImage)
    if (MalCovers.isFresh(cover.malKey)) return
    binding.resolve(this)
}

private class MalCoverBinding(
    view: SimpleDraweeView,
    private val cover: MediaCoverImageModel,
    private val large: Boolean
) : BaseControllerListener<ImageInfo>(), MalCovers.Listener {
    private val view = WeakReference(view)
    private var isLoading = false

    fun show(view: SimpleDraweeView, malImage: String?) {
        if (malImage == null) {
            showAniList(view)
            return
        }
        view.controller = Fresco.newDraweeControllerBuilder()
            .setOldController(view.controller)
            .setUri(malImage)
            .setControllerListener(this)
            .build()
    }

    fun resolve(view: SimpleDraweeView) {
        showLoading(view)
        MalCovers.resolve(cover.malKey, this)
    }

    fun hideLoading(view: SimpleDraweeView) {
        if (!isLoading) return
        isLoading = false
        view.hierarchy.setOverlayImage(null)
    }

    override fun onFailure(id: String?, throwable: Throwable?) {
        val view = boundView() ?: return
        showAniList(view)
        val isRefreshing = MalCovers.refresh(cover.malKey, this)
        if (isRefreshing) showLoading(view)
    }

    override fun onCoverResolveEnded(isChanged: Boolean) {
        val view = boundView() ?: return
        hideLoading(view)
        if (!isChanged) return
        val malImage = cover.malImage(large)
        show(view, malImage)
    }

    private fun showAniList(view: SimpleDraweeView) {
        val image = cover.aniListImage(large)
        view.setImageURI(image)
    }

    private fun showLoading(view: SimpleDraweeView) {
        if (isLoading) return
        isLoading = true
        val density = view.resources.displayMetrics.density
        val loading = MalCoverLoadingDrawable(density)
        view.hierarchy.setOverlayImage(loading)
    }

    private fun boundView(): SimpleDraweeView? {
        val view = view.get() ?: return null
        val isBound = view.getTag(R.id.malCoverBinding) === this
        return if (isBound) view else null
    }
}

private class MalCoverLoadingDrawable(density: Float) : Drawable() {
    companion object {
        private const val RADIUS_DP = 6f
        private const val MARGIN_DP = 5f
        private const val OUTLINE_DP = 2f
        private const val OPACITY = 0.1f
        private const val MAX_ALPHA = 255
    }

    private val radius = RADIUS_DP * density
    private val margin = MARGIN_DP * density
    private val outlineWidth = OUTLINE_DP * density
    private val outlineRadius = radius + outlineWidth / 2

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = outlineWidth
    }

    private var centerX = 0f
    private var centerY = 0f

    init {
        alpha = MAX_ALPHA
    }

    override fun onBoundsChange(bounds: Rect) {
        val offset = margin + outlineWidth + radius
        centerX = bounds.right - offset
        centerY = bounds.top + offset
    }

    override fun draw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, radius, dotPaint)
        canvas.drawCircle(centerX, centerY, outlineRadius, outlinePaint)
    }

    override fun setAlpha(alpha: Int) {
        val scaledAlpha = (alpha * OPACITY).toInt()
        dotPaint.alpha = scaledAlpha
        outlinePaint.alpha = scaledAlpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        dotPaint.colorFilter = colorFilter
        outlinePaint.colorFilter = colorFilter
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
