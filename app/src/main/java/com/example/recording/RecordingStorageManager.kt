package com.example.recording

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingStorageManager {
    private const val TAG = "RecordingStorage"
    const val FOLDER_NAME = "Arvexa Recorder"
    val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME"

    fun generateFilename(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = sdf.format(Date())
        return "Arvexa_Recording_$timestamp.mp4"
    }

    /**
     * Creates a pending MediaStore video entry in Movies/Arvexa Recorder
     */
    fun createPendingVideo(
        context: Context,
        filename: String,
        width: Int,
        height: Int
    ): Pair<Uri, ParcelFileDescriptor>? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.TITLE, filename.substringBeforeLast('.'))
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Video.Media.WIDTH, width)
                put(MediaStore.Video.Media.HEIGHT, height)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, contentValues)
                ?: return null

            val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: return null

            Pair(uri, pfd)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pending video entry: ${e.message}", e)
            null
        }
    }

    /**
     * Finalizes the MediaStore entry by clearing IS_PENDING so it immediately
     * displays in the device Gallery and photo pickers.
     */
    fun finalizeVideo(
        context: Context,
        uri: Uri,
        durationMs: Long,
        width: Int,
        height: Int,
        sizeBytes: Long
    ) {
        try {
            val contentValues = ContentValues().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                if (durationMs > 0) {
                    put(MediaStore.Video.Media.DURATION, durationMs)
                }
                if (width > 0) {
                    put(MediaStore.Video.Media.WIDTH, width)
                }
                if (height > 0) {
                    put(MediaStore.Video.Media.HEIGHT, height)
                }
                if (sizeBytes > 0) {
                    put(MediaStore.Video.Media.SIZE, sizeBytes)
                }
            }
            context.contentResolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize video entry: ${e.message}", e)
        }
    }

    /**
     * Deletes a video from MediaStore and device storage
     */
    fun deleteVideo(context: Context, uri: Uri): Boolean {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete video: ${e.message}", e)
            false
        }
    }

    /**
     * Renames a video file in MediaStore
     */
    fun renameVideo(context: Context, uri: Uri, newTitle: String): Boolean {
        return try {
            val newDisplayName = if (newTitle.endsWith(".mp4", ignoreCase = true)) {
                newTitle
            } else {
                "$newTitle.mp4"
            }
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newDisplayName)
                put(MediaStore.Video.Media.TITLE, newTitle.substringBeforeLast('.'))
            }
            val rows = context.contentResolver.update(uri, contentValues, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename video: ${e.message}", e)
            false
        }
    }

    /**
     * Returns total free space on primary shared storage in bytes.
     */
    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            1024L * 1024L * 1024L * 5 // 5 GB fallback
        }
    }

    fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            1024L * 1024L * 1024L * 64 // 64 GB fallback
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            val gb = mb / 1024
            String.format(Locale.US, "%.1f GB", gb)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    /**
     * Computes estimated recording time remaining given available bytes and target bitrate in bps.
     */
    fun getEstimatedRemainingMinutes(availableBytes: Long, bitrateBps: Int): Long {
        val bytesPerSecond = (bitrateBps + 192_000) / 8 // video + audio
        if (bytesPerSecond <= 0) return 0
        val safeBytes = (availableBytes - (500L * 1024 * 1024)).coerceAtLeast(0) // reserve 500MB safety buffer
        val totalSeconds = safeBytes / bytesPerSecond
        return (totalSeconds / 60).coerceAtLeast(0)
    }
}
