package com.revolgenx.anilib.entry.service

import com.revolgenx.anilib.common.data.model.FuzzyDateModel
import com.revolgenx.anilib.common.repository.util.Resource
import com.revolgenx.anilib.entry.data.field.SaveMediaListEntryField
import com.revolgenx.anilib.list.data.model.MediaListModel
import com.revolgenx.anilib.type.MediaListStatus
import com.revolgenx.anilib.type.MediaType
import io.reactivex.disposables.CompositeDisposable
import java.util.Calendar

fun MediaListEntryService.increaseProgress(
    item: MediaListModel,
    compositeDisposable: CompositeDisposable
) {
    val newProgress = (item.progress ?: 0) + 1
    val progressSaveField = ListProgressChange(
        entryId = item.id,
        mediaId = item.mediaId,
        status = item.status,
        progress = newProgress,
        total = item.media?.let {
            if (it.type == MediaType.MANGA.ordinal) it.chapters else it.episodes
        },
        startedAt = item.startedAt,
        completedAt = item.completedAt
    ).toSaveField()

    item.isProgressUpdating = true
    item.onDataChanged?.invoke(Resource.loading(item))

    saveMediaListEntry(progressSaveField, compositeDisposable) {
        if (it is Resource.Success) {
            val saved = it.data
            item.progress = saved?.progress ?: newProgress
            saved?.status?.let { status -> item.status = status }
            saved?.startedAt?.let { date -> item.startedAt = date }
            saved?.completedAt?.let { date -> item.completedAt = date }
        }
        item.isProgressUpdating = false
        item.onDataChanged?.invoke(it)
    }
}

/** Progress plus the status and date follow ups the site leaves to the user. */
class ListProgressChange(
    private val entryId: Int?,
    private val mediaId: Int?,
    private val status: Int?,
    private val progress: Int,
    private val total: Int?,
    private val startedAt: FuzzyDateModel?,
    private val completedAt: FuzzyDateModel?
) {
    private val completes get() = total != null && total > 0 && progress >= total
    private val startsWatching
        get() = progress >= 1 && (status == null || status == MediaListStatus.PLANNING.ordinal)

    val newStatus: Int?
        get() = when {
            completes && status != MediaListStatus.COMPLETED.ordinal -> MediaListStatus.COMPLETED.ordinal
            startsWatching -> MediaListStatus.CURRENT.ordinal
            else -> null
        }

    val startsToday get() = startsWatching && startedAt.isBlank()
    val completesToday get() = completes && completedAt.isBlank()

    fun toSaveField() = SaveMediaListEntryField().also { field ->
        field.id = entryId
        field.mediaId = mediaId
        field.progress = progress
        field.status = newStatus
        if (startsToday) field.startedAt = todayFuzzyDate()
        if (completesToday) field.completedAt = todayFuzzyDate()
    }

    private fun FuzzyDateModel?.isBlank() = this == null || isEmpty()
}

fun todayFuzzyDate(): FuzzyDateModel = Calendar.getInstance().let {
    FuzzyDateModel(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
}
