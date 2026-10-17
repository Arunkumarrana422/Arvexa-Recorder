package com.example.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ArvexaDatabase
import com.example.data.RecordingEntity
import com.example.model.AudioSourceOption
import com.example.model.CountdownOption
import com.example.model.FpsOption
import com.example.model.RecordingConfig
import com.example.model.RecordingItem
import com.example.model.RecordingStatus
import com.example.model.ResolutionOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreenRecordService : Service() {

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val NOTIFICATION_ID = 9110
        private const val CHANNEL_ID = "arvexa_recorder_channel"

        const val ACTION_START = "com.arvexa.recorder.START"
        const val ACTION_PAUSE = "com.arvexa.recorder.PAUSE"
        const val ACTION_RESUME = "com.arvexa.recorder.RESUME"
        const val ACTION_STOP = "com.arvexa.recorder.STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RESOLUTION = "extra_resolution"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_AUDIO_SOURCE = "extra_audio_source"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val EXTRA_BITRATE = "extra_bitrate"

        private val _recordingState = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
        val recordingState: StateFlow<RecordingStatus> = _recordingState.asStateFlow()

        fun isRecordingActive(): Boolean {
            val s = _recordingState.value
            return s is RecordingStatus.Recording || s is RecordingStatus.Countdown || s is RecordingStatus.Finalizing
        }

        fun resetState() {
            _recordingState.value = RecordingStatus.Idle
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var mediaProjection: MediaProjection? = null
    private var recorderEngine: ScreenRecorderEngine? = null
    private var notificationManager: NotificationManager? = null

    private var activeConfig: RecordingConfig = RecordingConfig()
    private var currentDurationMs: Long = 0L
    private var isCurrentlyPaused = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system")
            handleStop()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordService = this@ScreenRecordService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        Log.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != 0 && resultData != null) {
                    val resName = intent.getStringExtra(EXTRA_RESOLUTION) ?: ResolutionOption.RES_1080P.name
                    val fpsVal = intent.getIntExtra(EXTRA_FPS, 60)
                    val audioName = intent.getStringExtra(EXTRA_AUDIO_SOURCE) ?: AudioSourceOption.INTERNAL_ONLY.name
                    val countdownSec = intent.getIntExtra(EXTRA_COUNTDOWN, 3)
                    val customBitrate = intent.getIntExtra(EXTRA_BITRATE, -1).takeIf { it > 0 }

                    activeConfig = RecordingConfig(
                        resolution = ResolutionOption.valueOf(resName),
                        fps = FpsOption.fromValue(fpsVal),
                        audioSource = AudioSourceOption.valueOf(audioName),
                        countdown = CountdownOption.entries.find { it.seconds == countdownSec } ?: CountdownOption.THREE_SEC,
                        customBitrate = customBitrate
                    )

                    handleStart(resultCode, resultData)
                } else {
                    Log.e(TAG, "Missing resultCode or resultData for ACTION_START")
                    _recordingState.value = RecordingStatus.Error("Screen projection permission was denied.")
                    stopSelf()
                }
            }
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
        }

        return START_NOT_STICKY
    }

    private fun handleStart(resultCode: Int, resultData: Intent) {
        // Build and display foreground notification immediately to satisfy Android 14+ FGS requirements
        val initialNotification = buildNotification("Initializing recording…", isPaused = false)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            val hasMicPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            type
        } else {
            0
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // CRITICAL FOR ANDROID 14+ (API 34+):
        // 1. Obtain MediaProjection immediately after startForeground on the main thread
        // 2. Register MediaProjection.Callback BEFORE any VirtualDisplay or capture actions
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mpManager.getMediaProjection(resultCode, resultData)
            if (mp == null) {
                _recordingState.value = RecordingStatus.Error("Failed to obtain screen capture permission.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            mediaProjection = mp
            mp.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e(TAG, "Exception obtaining MediaProjection: ${e.message}", e)
            _recordingState.value = RecordingStatus.Error("Screen capture permission error: ${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        serviceScope.launch {
            // Check countdown
            if (activeConfig.countdown.seconds > 0) {
                for (remaining in activeConfig.countdown.seconds downTo 1) {
                    _recordingState.value = RecordingStatus.Countdown(remaining)
                    delay(1000)
                }
            }

            try {
                val mp = mediaProjection
                if (mp == null) {
                    _recordingState.value = RecordingStatus.Error("Media projection was disconnected.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                recorderEngine = ScreenRecorderEngine(
                    context = applicationContext,
                    mediaProjection = mp,
                    config = activeConfig,
                    onProgress = { durationMs ->
                        currentDurationMs = durationMs
                        _recordingState.value = RecordingStatus.Recording(
                            durationMs = durationMs,
                            isPaused = isCurrentlyPaused,
                            internalAudioActive = recorderEngine?.isInternalAudioActive == true
                        )
                        updateNotification(durationMs, isCurrentlyPaused)
                    },
                    onWarning = { warning ->
                        val current = _recordingState.value
                        if (current is RecordingStatus.Recording) {
                            _recordingState.value = current.copy(internalAudioWarning = warning)
                        }
                    },
                    onError = { errorMsg ->
                        _recordingState.value = RecordingStatus.Error(errorMsg)
                        handleStop()
                    },
                    onFinished = { savedItem ->
                        saveRecordingToDatabase(savedItem)
                        _recordingState.value = RecordingStatus.Saved(
                            uri = savedItem.uriString,
                            filePath = savedItem.filePath,
                            filename = savedItem.title,
                            durationMs = savedItem.durationMs,
                            sizeBytes = savedItem.fileSizeBytes,
                            resolution = savedItem.resolution
                        )
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                )

                val success = recorderEngine!!.start()
                if (!success) {
                    if (_recordingState.value !is RecordingStatus.Error) {
                        _recordingState.value = RecordingStatus.Error("Failed to start screen capture.")
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    _recordingState.value = RecordingStatus.Recording(
                        durationMs = 0L,
                        isPaused = false,
                        internalAudioActive = recorderEngine?.isInternalAudioActive == true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting screen capture: ${e.message}", e)
                _recordingState.value = RecordingStatus.Error("Screen capture failed: ${e.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun handlePause() {
        isCurrentlyPaused = true
        recorderEngine?.pause()
        val current = _recordingState.value
        if (current is RecordingStatus.Recording) {
            _recordingState.value = current.copy(isPaused = true)
        }
        updateNotification(currentDurationMs, isPaused = true)
    }

    private fun handleResume() {
        isCurrentlyPaused = false
        recorderEngine?.resume()
        val current = _recordingState.value
        if (current is RecordingStatus.Recording) {
            _recordingState.value = current.copy(isPaused = false)
        }
        updateNotification(currentDurationMs, isPaused = false)
    }

    private fun handleStop() {
        _recordingState.value = RecordingStatus.Finalizing
        updateNotification(currentDurationMs, isPaused = false, isFinalizing = true)

        serviceScope.launch(Dispatchers.IO) {
            try {
                recorderEngine?.stop()
                try {
                    mediaProjection?.unregisterCallback(projectionCallback)
                } catch (_: Exception) {}
                mediaProjection?.stop()
                mediaProjection = null
            } catch (e: Exception) {
                Log.e(TAG, "Exception during stop: ${e.message}")
            }
        }
    }

    private fun saveRecordingToDatabase(item: RecordingItem) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = ArvexaDatabase.getInstance(applicationContext)
                db.recordingDao().insertRecording(RecordingEntity.fromDomain(item))
                Log.d(TAG, "Saved recording to database: ${item.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save recording entity: ${e.message}", e)
            }
        }
    }

    private fun updateNotification(durationMs: Long, isPaused: Boolean, isFinalizing: Boolean = false) {
        val totalSec = durationMs / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        val timeStr = String.format("%02d:%02d", mm, ss)

        val text = when {
            isFinalizing -> "Finalizing & saving video to Gallery…"
            isPaused -> "Paused at $timeStr • Tap Resume to continue"
            else -> "Recording: $timeStr (${activeConfig.resolution.shortLabel} ${activeConfig.fps.fps}FPS)"
        }

        val notification = buildNotification(text, isPaused)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String, isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeActionTitle = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arvexa Screen Recorder")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, pauseResumeActionTitle, pauseResumePendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Controls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows recording timer and controls to pause or stop screen recordings."
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ScreenRecordService destroyed")
        recorderEngine?.stop()
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {}
        mediaProjection?.stop()
        mediaProjection = null
    }
}
