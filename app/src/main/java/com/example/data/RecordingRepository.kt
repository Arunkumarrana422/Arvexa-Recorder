package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.model.RecordingItem
import com.example.recording.RecordingStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RecordingRepository(
    private val context: Context,
    private val recordingDao: RecordingDao
) {
    companion object {
        private const val TAG = "RecordingRepository"
    }

    val allRecordings: Flow<List<RecordingItem>> =
        recordingDao.getAllRecordings().map { list ->
            list.map { it.toDomain() }
        }

    val recentRecordings: Flow<List<RecordingItem>> =
        recordingDao.getRecentRecordings(limit = 4).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun deleteRecording(item: RecordingItem): Boolean = withContext(Dispatchers.IO) {
        var deletedMedia = false
        try {
            val uri = Uri.parse(item.uriString)
            deletedMedia = RecordingStorageManager.deleteVideo(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting media: ${e.message}")
        }
        recordingDao.deleteById(item.id)
        true
    }

    suspend fun renameRecording(item: RecordingItem, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        val cleanTitle = if (newTitle.endsWith(".mp4", ignoreCase = true)) {
            newTitle
        } else {
            "$newTitle.mp4"
        }

        try {
            val uri = Uri.parse(item.uriString)
            RecordingStorageManager.renameVideo(context, uri, cleanTitle)
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming MediaStore entry: ${e.message}")
        }

        recordingDao.updateTitle(item.id, cleanTitle)
        true
    }

    suspend fun syncFromMediaStore() = withContext(Dispatchers.IO) {
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.RELATIVE_PATH
            )

            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%${RecordingStorageManager.FOLDER_NAME}%")

            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Recording"
                    val duration = cursor.getLong(durCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000L

                    val uri = ContentUris.withAppendedId(collection, mediaId)

                    // Check if already in database
                    val existing = recordingDao.getRecordingByUri(uri.toString())
                    if (existing == null) {
                        recordingDao.insertRecording(
                            RecordingEntity(
                                title = name,
                                uriString = uri.toString(),
                                filePath = "${RecordingStorageManager.RELATIVE_PATH}/$name",
                                durationMs = duration,
                                resolution = "${width}×${height}",
                                fps = 60,
                                audioSource = "Internal / Game",
                                fileSizeBytes = size,
                                dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from MediaStore: ${e.message}")
        }
    }
}
