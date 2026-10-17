package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ArvexaDatabase
import com.example.data.RecordingRepository
import com.example.model.AudioSourceOption
import com.example.model.CountdownOption
import com.example.model.FpsOption
import com.example.model.RecordingConfig
import com.example.model.RecordingItem
import com.example.model.RecordingStatus
import com.example.model.ResolutionOption
import com.example.recording.RecordingStorageManager
import com.example.recording.ScreenRecordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StorageInfo(
    val availableBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val availableFormatted: String = "Calculating…",
    val estimatedMinutesRemaining: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecordingRepository

    private val _config = MutableStateFlow(RecordingConfig())
    val config: StateFlow<RecordingConfig> = _config.asStateFlow()

    val recordingState: StateFlow<RecordingStatus> = ScreenRecordService.recordingState

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _activePlaybackItem = MutableStateFlow<RecordingItem?>(null)
    val activePlaybackItem: StateFlow<RecordingItem?> = _activePlaybackItem.asStateFlow()

    private val _savedVideoDialog = MutableStateFlow<RecordingStatus.Saved?>(null)
    val savedVideoDialog: StateFlow<RecordingStatus.Saved?> = _savedVideoDialog.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    val allRecordings: StateFlow<List<RecordingItem>>
    val recentRecordings: StateFlow<List<RecordingItem>>

    init {
        val database = ArvexaDatabase.getInstance(application)
        repository = RecordingRepository(application, database.recordingDao())

        allRecordings = repository.allRecordings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        recentRecordings = repository.recentRecordings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        updateStorageInfo()
        syncRecordings()

        // Observe recording status changes to pop up the Success dialog when recording is saved
        viewModelScope.launch {
            recordingState.collect { state ->
                if (state is RecordingStatus.Saved) {
                    _savedVideoDialog.value = state
                    updateStorageInfo()
                }
            }
        }
    }

    fun updateResolution(resolution: ResolutionOption) {
        _config.value = _config.value.copy(resolution = resolution)
        updateStorageInfo()
    }

    fun updateFps(fps: FpsOption) {
        _config.value = _config.value.copy(fps = fps)
    }

    fun updateAudioSource(source: AudioSourceOption) {
        _config.value = _config.value.copy(audioSource = source)
    }

    fun updateCountdown(countdown: CountdownOption) {
        _config.value = _config.value.copy(countdown = countdown)
    }

    fun updateCustomBitrate(bitrateBps: Int?) {
        _config.value = _config.value.copy(customBitrate = bitrateBps)
        updateStorageInfo()
    }

    fun startRecording(resultCode: Int, resultData: Intent) {
        val context = getApplication<Application>()
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenRecordService.EXTRA_RESOLUTION, _config.value.resolution.name)
            putExtra(ScreenRecordService.EXTRA_FPS, _config.value.fps.fps)
            putExtra(ScreenRecordService.EXTRA_AUDIO_SOURCE, _config.value.audioSource.name)
            putExtra(ScreenRecordService.EXTRA_COUNTDOWN, _config.value.countdown.seconds)
            _config.value.customBitrate?.let {
                putExtra(ScreenRecordService.EXTRA_BITRATE, it)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun deleteRecording(item: RecordingItem) {
        viewModelScope.launch {
            repository.deleteRecording(item)
            updateStorageInfo()
            _userFeedbackMessage.value = "Recording deleted successfully"
        }
    }

    fun renameRecording(item: RecordingItem, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecording(item, newTitle)
            _userFeedbackMessage.value = "Recording renamed to $newTitle"
        }
    }

    fun playVideo(item: RecordingItem) {
        _activePlaybackItem.value = item
    }

    fun dismissVideoPlayer() {
        _activePlaybackItem.value = null
    }

    fun dismissSavedDialog() {
        _savedVideoDialog.value = null
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }

    fun updateStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = RecordingStorageManager.getAvailableStorageBytes()
            val total = RecordingStorageManager.getTotalStorageBytes()
            val minutes = RecordingStorageManager.getEstimatedRemainingMinutes(
                available,
                _config.value.effectiveBitrate
            )
            _storageInfo.value = StorageInfo(
                availableBytes = available,
                totalBytes = total,
                availableFormatted = "${RecordingStorageManager.formatBytes(available)} free",
                estimatedMinutesRemaining = minutes
            )
        }
    }

    fun syncRecordings() {
        viewModelScope.launch {
            repository.syncFromMediaStore()
        }
    }
}
