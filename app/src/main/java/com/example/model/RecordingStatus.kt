package com.example.model

sealed interface RecordingStatus {
    data object Idle : RecordingStatus

    data class Countdown(val remainingSeconds: Int) : RecordingStatus

    data class Recording(
        val durationMs: Long = 0L,
        val isPaused: Boolean = false,
        val internalAudioActive: Boolean = false,
        val internalAudioWarning: String? = null
    ) : RecordingStatus

    data object Finalizing : RecordingStatus

    data class Saved(
        val uri: String,
        val filePath: String,
        val filename: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val resolution: String
    ) : RecordingStatus

    data class Error(val message: String) : RecordingStatus
}
