package com.example.recording

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.example.model.AudioSourceOption
import com.example.model.RecordingConfig
import com.example.model.RecordingItem
import com.example.model.ResolutionOption
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecorderEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val config: RecordingConfig,
    private val onProgress: (durationMs: Long) -> Unit,
    private val onWarning: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onFinished: (RecordingItem) -> Unit
) {
    companion object {
        private const val TAG = "ScreenRecorderEngine"
        private const val TIMEOUT_USEC = 10_000L
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_CHANNELS = 2
        private const val AUDIO_BITRATE = 192_000
    }

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // Hardware & Media components
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaMuxer: MediaMuxer? = null
    private var tempRecordingFile: File? = null
    private var outputUri: Uri? = null
    private var audioCaptureManager: AudioCaptureManager? = null

    // Background threads
    private var videoDrainThread: Thread? = null
    private var audioDrainThread: Thread? = null
    private var timerThread: Thread? = null

    // Muxer state
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private val muxerLock = Any()
    private var hasAudioCapture = false

    private var recordWidth = 1080
    private var recordHeight = 1920
    private var recordDpi = 320

    private var startTimeMs = 0L
    private var totalPausedDurationMs = 0L
    private var pauseStartTimeMs = 0L
    private var filename = ""

    val isInternalAudioActive: Boolean
        get() = audioCaptureManager?.isInternalAudioActive == true

    fun start(): Boolean {
        try {
            calculateDimensions()

            filename = RecordingStorageManager.generateFilename()
            // Allocate pending entry in MediaStore
            val pending = RecordingStorageManager.createPendingVideo(
                context = context,
                filename = filename,
                width = recordWidth,
                height = recordHeight
            )

            if (pending == null) {
                onError("Failed to create video entry in Movies/Arvexa Recorder.")
                return false
            }

            outputUri = pending.first
            try {
                pending.second.close() // Close temporary pfd; we record to cache file and stream into uri
            } catch (_: Exception) {}

            // Setup local temp file for reliable seeking and MP4 header writing
            val tempFile = File(context.cacheDir, "arvexa_${System.currentTimeMillis()}.mp4")
            if (tempFile.exists()) tempFile.delete()
            tempRecordingFile = tempFile

            mediaMuxer = MediaMuxer(
                tempFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // Setup Video Encoder
            val videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                recordWidth,
                recordHeight
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, config.effectiveBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            videoEncoder = try {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurface = createInputSurface()
                    start()
                }
            } catch (codecEx: Exception) {
                Log.e(TAG, "Failed to configure AVC encoder at ${recordWidth}x${recordHeight}: ${codecEx.message}")
                // Fallback to safe 720x1280 (or 1280x720) if custom dimensions failed
                val (fallbackW, fallbackH) = if (recordHeight >= recordWidth) Pair(720, 1280) else Pair(1280, 720)
                recordWidth = fallbackW
                recordHeight = fallbackH
                videoFormat.setInteger(MediaFormat.KEY_WIDTH, fallbackW)
                videoFormat.setInteger(MediaFormat.KEY_HEIGHT, fallbackH)
                videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurface = createInputSurface()
                    start()
                }
            }

            // Setup Audio Encoder if audio is requested
            hasAudioCapture = false
            if (config.audioSource != AudioSourceOption.MUTE) {
                try {
                    val audioFormat = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        AUDIO_SAMPLE_RATE,
                        AUDIO_CHANNELS
                    ).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                    }

                    val aEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                        configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        start()
                    }

                    val aCapture = AudioCaptureManager(
                        audioSourceOption = config.audioSource,
                        mediaProjection = mediaProjection,
                        sampleRate = AUDIO_SAMPLE_RATE,
                        onAudioDataAvailable = { pcmData, length, ptsUs ->
                            feedAudioToEncoder(pcmData, length, ptsUs)
                        },
                        onWarning = onWarning
                    )

                    val audioStarted = aCapture.start()
                    if (audioStarted) {
                        audioEncoder = aEncoder
                        audioCaptureManager = aCapture
                        hasAudioCapture = true
                    } else {
                        Log.w(TAG, "Audio capture could not be initialized. Continuing video recording without sound.")
                        onWarning("Audio capture not supported by device/game. Recording video only.")
                        try {
                            aEncoder.stop()
                            aEncoder.release()
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio encoder setup exception: ${e.message}. Continuing with video only.")
                    onWarning("Audio capture unavailable. Video recording will proceed.")
                    try {
                        audioEncoder?.release()
                    } catch (_: Exception) {}
                    audioEncoder = null
                }
            }

            // Create Virtual Display mirroring screen onto encoder's input surface
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ArvexaScreenCapture",
                recordWidth,
                recordHeight,
                recordDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )

            isRunning.set(true)
            isPaused.set(false)
            startTimeMs = System.currentTimeMillis()

            startDrainThreads(hasAudioCapture)
            startTimerThread()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenRecorderEngine: ${e.message}", e)
            onError("Recording initialization error: ${e.message}")
            cleanup()
            return false
        }
    }

    private fun calculateDimensions() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        recordDpi = metrics.densityDpi
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val isPortrait = screenHeight >= screenWidth

        val maxAllowedLongSide = when (config.resolution) {
            ResolutionOption.RES_1080P -> 1920
            ResolutionOption.RES_720P -> 1280
            ResolutionOption.RES_480P -> 854
        }

        // Calculate aspect ratio
        val (screenLong, screenShort) = if (isPortrait) {
            Pair(screenHeight, screenWidth)
        } else {
            Pair(screenWidth, screenHeight)
        }

        val aspectRatio = screenShort.toDouble() / screenLong.toDouble()

        var chosenLong = minOf(maxAllowedLongSide, screenLong)
        var chosenShort = (chosenLong * aspectRatio).toInt()

        // H.264 encoders strictly require dimensions to be multiples of 16
        chosenLong = (chosenLong / 16) * 16
        chosenShort = (chosenShort / 16) * 16

        // Clamp minimum safe dimensions
        chosenLong = chosenLong.coerceAtLeast(320)
        chosenShort = chosenShort.coerceAtLeast(240)

        if (isPortrait) {
            recordWidth = chosenShort
            recordHeight = chosenLong
        } else {
            recordWidth = chosenLong
            recordHeight = chosenShort
        }

        Log.d(TAG, "Calculated safe dimensions: ${recordWidth}x${recordHeight} (screen was ${screenWidth}x${screenHeight})")
    }

    private fun feedAudioToEncoder(pcmData: ByteArray, length: Int, ptsUs: Long) {
        val encoder = audioEncoder ?: return
        if (!isRunning.get() || isPaused.get()) return

        try {
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(pcmData, 0, length)

                val adjustedPts = ptsUs - (totalPausedDurationMs * 1000L)
                encoder.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    length,
                    adjustedPts.coerceAtLeast(0L),
                    0
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error queuing audio buffer: ${e.message}")
        }
    }

    private fun startDrainThreads(hasAudio: Boolean) {
        videoDrainThread = Thread({
            drainVideoEncoder(hasAudio)
        }, "ArvexaVideoDrainThread").apply { start() }

        if (hasAudio) {
            audioDrainThread = Thread({
                drainAudioEncoder()
            }, "ArvexaAudioDrainThread").apply { start() }
        }
    }

    private fun drainVideoEncoder(hasAudio: Boolean) {
        val encoder = videoEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        var waitAudioIterations = 0
        var prevVideoPtsUs = -1L

        while (isRunning.get()) {
            try {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxerLock) {
                            if (!isMuxerStarted) {
                                val newFormat = encoder.outputFormat
                                videoTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                Log.d(TAG, "Video track added index: $videoTrackIndex")

                                if (!hasAudio || audioTrackIndex != -1) {
                                    mediaMuxer?.start()
                                    isMuxerStarted = true
                                    Log.d(TAG, "MediaMuxer started by video thread")
                                }
                            }
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                        if (encodedData != null) {
                            // Filter out codec-specific configuration data buffer (SPS/PPS)
                            // because MediaMuxer already received CSD via INFO_OUTPUT_FORMAT_CHANGED.
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }

                            if (bufferInfo.size > 0) {
                                synchronized(muxerLock) {
                                    // If waiting for audio track times out (> 1 sec), start muxer with video only
                                    if (!isMuxerStarted && hasAudio && videoTrackIndex != -1) {
                                        waitAudioIterations++
                                        if (waitAudioIterations > 50) {
                                            Log.w(TAG, "Audio track timed out, starting muxer with video track only")
                                            try {
                                                mediaMuxer?.start()
                                                isMuxerStarted = true
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Failed to start muxer: ${e.message}")
                                            }
                                        }
                                    }

                                    if (isMuxerStarted && !isPaused.get()) {
                                        encodedData.position(bufferInfo.offset)
                                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                                        bufferInfo.presentationTimeUs -= (totalPausedDurationMs * 1000L)
                                        bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)

                                        // Ensure strictly monotonically increasing PTS
                                        if (bufferInfo.presentationTimeUs <= prevVideoPtsUs) {
                                            bufferInfo.presentationTimeUs = prevVideoPtsUs + 1000L
                                        }
                                        prevVideoPtsUs = bufferInfo.presentationTimeUs

                                        try {
                                            mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Error writing video sample: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Exception during video drain: ${e.message}")
                }
                break
            }
        }
    }

    private fun drainAudioEncoder() {
        val encoder = audioEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        var prevAudioPtsUs = -1L

        while (isRunning.get()) {
            try {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxerLock) {
                            if (!isMuxerStarted) {
                                val newFormat = encoder.outputFormat
                                audioTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                Log.d(TAG, "Audio track added index: $audioTrackIndex")

                                if (videoTrackIndex != -1) {
                                    mediaMuxer?.start()
                                    isMuxerStarted = true
                                    Log.d(TAG, "MediaMuxer started by audio thread")
                                }
                            }
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                        if (encodedData != null) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }

                            if (bufferInfo.size > 0) {
                                synchronized(muxerLock) {
                                    if (isMuxerStarted && !isPaused.get()) {
                                        encodedData.position(bufferInfo.offset)
                                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                                        bufferInfo.presentationTimeUs -= (totalPausedDurationMs * 1000L)
                                        bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)

                                        // Ensure strictly monotonically increasing PTS
                                        if (bufferInfo.presentationTimeUs <= prevAudioPtsUs) {
                                            bufferInfo.presentationTimeUs = prevAudioPtsUs + 1000L
                                        }
                                        prevAudioPtsUs = bufferInfo.presentationTimeUs

                                        try {
                                            mediaMuxer?.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Error writing audio sample: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Exception during audio drain: ${e.message}")
                }
                break
            }
        }
    }

    private fun startTimerThread() {
        timerThread = Thread({
            while (isRunning.get()) {
                if (!isPaused.get()) {
                    val currentDuration = (System.currentTimeMillis() - startTimeMs - totalPausedDurationMs).coerceAtLeast(0L)
                    onProgress(currentDuration)
                }
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "ArvexaTimerThread").apply { start() }
    }

    fun pause() {
        if (!isRunning.get() || isPaused.get()) return
        isPaused.set(true)
        pauseStartTimeMs = System.currentTimeMillis()
        audioCaptureManager?.pause()
        Log.d(TAG, "Screen recording paused.")
    }

    fun resume() {
        if (!isRunning.get() || !isPaused.get()) return
        val pausedDuration = System.currentTimeMillis() - pauseStartTimeMs
        if (pausedDuration > 0) {
            totalPausedDurationMs += pausedDuration
        }
        isPaused.set(false)
        audioCaptureManager?.resume()
        Log.d(TAG, "Screen recording resumed.")
    }

    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false)
        isPaused.set(false)

        val totalDurationMs = (System.currentTimeMillis() - startTimeMs - totalPausedDurationMs).coerceAtLeast(0L)

        try {
            videoEncoder?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.w(TAG, "Error signalling video EOS: ${e.message}")
        }

        try {
            audioEncoder?.let { enc ->
                val inputIndex = enc.dequeueInputBuffer(TIMEOUT_USEC)
                if (inputIndex >= 0) {
                    enc.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error signalling audio EOS: ${e.message}")
        }

        audioCaptureManager?.stop()

        try {
            videoDrainThread?.join(1000)
            audioDrainThread?.join(1000)
            timerThread?.interrupt()
        } catch (_: Exception) {}

        var finalSize = 0L
        try {
            synchronized(muxerLock) {
                if (isMuxerStarted) {
                    try {
                        mediaMuxer?.stop()
                    } catch (muxerEx: Exception) {
                        Log.w(TAG, "MediaMuxer stop warning (harmless): ${muxerEx.message}")
                    }
                    try {
                        mediaMuxer?.release()
                    } catch (_: Exception) {}
                }
            }
            mediaMuxer = null

            // Copy temp file into MediaStore URI
            val tempFile = tempRecordingFile
            val uri = outputUri
            if (tempFile != null && tempFile.exists() && uri != null) {
                finalSize = tempFile.length()
                context.contentResolver.openOutputStream(uri)?.use { outStream ->
                    tempFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                tempFile.delete()

                RecordingStorageManager.finalizeVideo(
                    context = context,
                    uri = uri,
                    durationMs = totalDurationMs,
                    width = recordWidth,
                    height = recordHeight,
                    sizeBytes = finalSize
                )

                val item = RecordingItem(
                    title = filename,
                    uriString = uri.toString(),
                    filePath = "${RecordingStorageManager.RELATIVE_PATH}/$filename",
                    durationMs = totalDurationMs,
                    resolution = "${recordWidth}×${recordHeight}",
                    fps = config.fps.fps,
                    audioSource = config.audioSource.title,
                    fileSizeBytes = finalSize,
                    dateAdded = System.currentTimeMillis()
                )

                onFinished(item)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording: ${e.message}", e)
            onError("Failed to finalize recording: ${e.message}")
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (_: Exception) {}

        try {
            inputSurface?.release()
            inputSurface = null
        } catch (_: Exception) {}

        try {
            videoEncoder?.stop()
            videoEncoder?.release()
            videoEncoder = null
        } catch (_: Exception) {}

        try {
            audioEncoder?.stop()
            audioEncoder?.release()
            audioEncoder = null
        } catch (_: Exception) {}

        audioCaptureManager?.stop()
        audioCaptureManager = null
    }
}
