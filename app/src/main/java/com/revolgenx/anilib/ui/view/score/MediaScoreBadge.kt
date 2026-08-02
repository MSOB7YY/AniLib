package com.revolgenx.anilib.ui.view.score

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.pranavpandey.android.dynamic.support.widget.DynamicImageView
import com.pranavpandey.android.dynamic.support.widget.DynamicTextView
import com.pranavpandey.android.dynamic.theme.Theme
import com.revolgenx.anilib.R
import com.revolgenx.anilib.common.preference.hideGlobalRating
import com.revolgenx.anilib.util.dp
import com.revolgenx.anilib.util.naText

class MediaScoreBadge : LinearLayout {

    private var scoreImage: DynamicImageView
    private var scoreTv: DynamicTextView
    private var isListScore = false

    private val scoreImageMarginEnd = dp(6f)

    /** What the badge would show if nothing were hidden. */
    private var scoreLabel: String? = null

    /** Null means "leave whatever icon is already set", which is how list scores behave. */
    @DrawableRes
    private var scoreIcon: Int? = null

    private var isRevealed = false

    /**
     * A global score is everyone else's opinion, so it is what the spoiler preference hides.
     * A list score is the user's own and is always shown.
     */
    private val isHidden: Boolean
        get() = !isListScore && !isRevealed && hideGlobalRating()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)
    constructor(context: Context, attributeSet: AttributeSet?, defStyle: Int) : super(
        context,
        attributeSet,
        defStyle
    ) {

        val a = context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.MediaScoreBadge,
            defStyle,
            0
        )

        try {
            isListScore = a.getBoolean(R.styleable.MediaScoreBadge_isListScore, false);
        } catch (ex: Exception) {

        } finally {
            a.recycle()
        }

        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.oval_background)

        setPadding(dp(5f), dp(3f), dp(5f), dp(3f))

        scoreImage = DynamicImageView(context, attributeSet, defStyle).also {
            it.layoutParams = LayoutParams(dp(12f), dp(12f)).also { params ->
                params.gravity = Gravity.CENTER
                params.marginEnd = scoreImageMarginEnd
            }
            it.contrastWithColor = Theme.Color.UNKNOWN
            it.color = Color.WHITE
            it.setImageResource(R.drawable.ic_star)
        }

        scoreTv = DynamicTextView(context, attributeSet, defStyle).also {
            it.layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also { params ->
                    params.gravity = Gravity.CENTER_VERTICAL
                }
            it.includeFontPadding = false
            it.setSingleLine()
            it.contrastWithColor = Theme.Color.UNKNOWN
            it.color = Color.WHITE
            it.gravity = Gravity.CENTER_VERTICAL
            it.textSize = 10f
        }

        addView(scoreImage)
        addView(scoreTv)

        setOnClickListener {
            isRevealed = !isRevealed
            render()
        }

        render()
    }


    private var _scoreTextVisibility: Int = View.VISIBLE

    var scoreTextVisibility: Int
        get() = _scoreTextVisibility
        set(value) {
            _scoreTextVisibility = value
            render()
        }


    var text: Int? = null
        set(value) {
            field = value
            scoreIcon = iconFor(value?.toDouble())
            scoreLabel = value?.toString()?.let {
                if (isListScore) {
                    it
                } else {
                    it.plus("%")
                }
            }.naText()
            onNewValueBound()
            render()
        }

    fun setText(score: Double?) {
        scoreIcon = iconFor(score)
        scoreLabel = score?.toString().naText()
        onNewValueBound()
        render()
    }

    /**
     * Badges are recycled, so anything a previous item turned on has to be cleared: the score
     * starts hidden again, and the text comes back for callers that only hide it for some score
     * formats.
     */
    private fun onNewValueBound() {
        isRevealed = false
        _scoreTextVisibility = View.VISIBLE
    }


    fun setImageResource(@DrawableRes imageRes: Int) {
        scoreIcon = imageRes
        render()
    }

    @DrawableRes
    private fun iconFor(score: Double?): Int? {
        if (isListScore) return null
        return when {
            score == null -> R.drawable.ic_star
            score >= 75 -> R.drawable.ic_score_smile
            score > 60 -> R.drawable.ic_score_neutral
            else -> R.drawable.ic_score_sad
        }
    }

    private fun render() {
        val hidden = isHidden

        if (hidden) {
            scoreImage.setImageResource(R.drawable.ic_eye)
        } else {
            scoreIcon?.let { scoreImage.setImageResource(it) }
        }

        scoreTv.text = if (hidden) null else scoreLabel
        scoreTv.visibility = if (hidden) View.GONE else _scoreTextVisibility

        // a lone icon should not keep the gap that only exists to separate it from the score
        val showsText = !hidden && _scoreTextVisibility == View.VISIBLE
        (scoreImage.layoutParams as LayoutParams).marginEnd =
            if (showsText) scoreImageMarginEnd else 0
        scoreImage.requestLayout()

        // stay transparent to touches unless there is something to reveal or re-hide, otherwise
        // the badge would swallow taps meant for the row underneath it
        isClickable = !isListScore && hideGlobalRating()

        contentDescription = if (hidden) context.getString(R.string.show_rating) else scoreLabel
    }

}
