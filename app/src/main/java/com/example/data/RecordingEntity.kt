package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.RecordingItem

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val uriString: String,
    val filePath: String,
    val durationMs: Long,
    val resolution: String,
    val fps: Int,
    val audioSource: String,
    val fileSizeBytes: Long,
    val dateAdded: Long
) {
    fun toDomain(): RecordingItem = RecordingItem(
        id = id,
        title = title,
        uriString = uriString,
        filePath = filePath,
        durationMs = durationMs,
        resolution = resolution,
        fps = fps,
        audioSource = audioSource,
        fileSizeBytes = fileSizeBytes,
        dateAdded = dateAdded
    )

    companion object {
        fun fromDomain(item: RecordingItem): RecordingEntity = RecordingEntity(
            id = item.id,
            title = item.title,
            uriString = item.uriString,
            filePath = item.filePath,
            durationMs = item.durationMs,
            resolution = item.resolution,
            fps = item.fps,
            audioSource = item.audioSource,
            fileSizeBytes = item.fileSizeBytes,
            dateAdded = item.dateAdded
        )
    }
}
