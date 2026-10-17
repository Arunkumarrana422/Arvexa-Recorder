package com.example.model

data class RecordingItem(
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
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (fileSizeBytes <= 0) return "0 MB"
            val mb = fileSizeBytes.toDouble() / (1024 * 1024)
            return if (mb >= 1024) {
                val gb = mb / 1024
                String.format("%.2f GB", gb)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
