package com.revolgenx.anilib.list.data.tag

import androidx.annotation.DrawableRes
import com.revolgenx.anilib.R

// by claude
/** Tags are `#hashtags` inside the entry notes, so they sync with the site at no extra request. */
object ListTags {

    val defaults = listOf(
        "day", "night", "sad", "happy", "chill", "action", "funny", "cute", "nostalgia"
    )

    private val tagRegex = Regex("(?:^|\\s)#([a-z0-9_]+)", RegexOption.IGNORE_CASE)
    private val invalidChars = Regex("[^a-z0-9_]")

    @DrawableRes
    fun iconOf(tag: String): Int? = when (tag) {
        "day" -> R.drawable.ic_tag_day
        "night" -> R.drawable.ic_tag_night
        "sad" -> R.drawable.ic_score_sad
        "happy" -> R.drawable.ic_score_smile
        "chill" -> R.drawable.ic_tag_chill
        "action" -> R.drawable.ic_fire
        "funny" -> R.drawable.ic_tag_funny
        "cute" -> R.drawable.ic_tag_cute
        "nostalgia" -> R.drawable.ic_history
        else -> null
    }

    fun parse(notes: String?): List<String> {
        if (notes.isNullOrBlank() || !notes.contains('#')) return emptyList()
        return tagRegex.findAll(notes)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .toList()
    }

    /** The notes without their tag tokens, what the user actually typed. */
    fun strip(notes: String?): String {
        if (notes.isNullOrBlank() || !notes.contains('#')) return notes.orEmpty()
        return notes.replace(tagRegex, "").trim()
    }

    /** Stripped notes with the tags appended on their own line, empty when there is nothing. */
    fun compose(notes: String?, tags: Collection<String>): String {
        val text = strip(notes)
        if (tags.isEmpty()) return text
        val line = tags.joinToString(" ") { "#$it" }
        return if (text.isEmpty()) line else "$text\n$line"
    }

    fun normalize(input: String): String? =
        input.trim().removePrefix("#").lowercase().replace(invalidChars, "").ifEmpty { null }
}
