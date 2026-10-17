package com.example.recording

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.example.model.AudioSourceOption
import java.util.concurrent.atomic.AtomicBoolean

class AudioCaptureManager(
    private val audioSourceOption: AudioSourceOption,
    private val mediaProjection: MediaProjection?,
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_STEREO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val onAudioDataAvailable: (ByteArray, Int, Long) -> Unit,
    private val onWarning: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
    }

    private val isCapturing = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var captureThread: Thread? = null

    private var internalAudioRecord: AudioRecord? = null
    private var micAudioRecord: AudioRecord? = null

    val isInternalAudioActive: Boolean
        get() = internalAudioRecord != null && internalAudioRecord?.state == AudioRecord.STATE_INITIALIZED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (audioSourceOption == AudioSourceOption.MUTE) {
            Log.d(TAG, "Audio muted by user preference.")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        // 1. Setup Internal Audio Capture if required
        if (audioSourceOption.requiresInternalAudioCapture()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                try {
                    val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build()

                    val format = AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()

                    val record = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(playbackConfig)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .build()

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        internalAudioRecord = record
                        Log.d(TAG, "Internal game audio capture initialized successfully.")
                    } else {
                        Log.w(TAG, "Internal audio capture failed to initialize.")
                        onWarning("Internal audio capture is not supported on this device state.")
                        record.release()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException initializing internal audio: ${e.message}")
                    onWarning("Internal audio permission restricted by OS. BGMI capture requires consent.")
                } catch (e: Exception) {
                    Log.e(TAG, "Exception initializing internal audio: ${e.message}")
                    onWarning("Failed to capture internal audio: ${e.message}")
                }
            } else {
                onWarning("Internal game audio recording requires Android 10+ and active screen projection.")
            }
        }

        // 2. Setup Mic Audio Capture if required
        if (audioSourceOption.requiresMicrophonePermission()) {
            try {
                val format = AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()

                val record = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    micAudioRecord = record
                    Log.d(TAG, "Microphone audio capture initialized successfully.")
                } else {
                    Log.w(TAG, "Microphone AudioRecord failed to initialize.")
                    record.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize microphone audio: ${e.message}")
                onWarning("Microphone audio could not be initialized: ${e.message}")
            }
        }

        if (internalAudioRecord == null && micAudioRecord == null) {
            if (audioSourceOption != AudioSourceOption.MUTE) {
                onWarning("No audio sources could be initialized. Video will record without sound.")
            }
            return false
        }

        isCapturing.set(true)
        isPaused.set(false)

        try {
            internalAudioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start internal AudioRecord: ${e.message}")
            onWarning("Internal audio capture could not start. BGMI policy may prohibit playback capture.")
        }

        try {
            micAudioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mic AudioRecord: ${e.message}")
        }

        captureThread = Thread({
            runCaptureLoop(bufferSize)
        }, "ArvexaAudioCaptureThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        return true
    }

    private fun runCaptureLoop(bufferSize: Int) {
        val internalBuffer = ByteArray(bufferSize)
        val micBuffer = ByteArray(bufferSize)
        val mixedBuffer = ByteArray(bufferSize)

        var totalFramesRead = 0L

        while (isCapturing.get()) {
            if (isPaused.get()) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            var internalRead = 0
            var micRead = 0

            val internalRec = internalAudioRecord
            if (internalRec != null && internalRec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                internalRead = internalRec.read(internalBuffer, 0, bufferSize)
            }

            val micRec = micAudioRecord
            if (micRec != null && micRec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                micRead = micRec.read(micBuffer, 0, bufferSize)
            }

            val validBytes: Int
            val outputBuffer: ByteArray

            when {
                internalRead > 0 && micRead > 0 -> {
                    // Mix both stereo PCM 16-bit streams
                    validBytes = minOf(internalRead, micRead)
                    mixPcm16Stereo(internalBuffer, micBuffer, mixedBuffer, validBytes)
                    outputBuffer = mixedBuffer
                }
                internalRead > 0 -> {
                    validBytes = internalRead
                    outputBuffer = internalBuffer
                }
                micRead > 0 -> {
                    validBytes = micRead
                    outputBuffer = micBuffer
                }
                else -> {
                    // When device/game is silent or in loading screen, generate silent PCM chunk
                    // to prevent audio track starvation and MPEG4Writer empty track errors
                    validBytes = 2048 // 512 stereo 16-bit frames
                    java.util.Arrays.fill(internalBuffer, 0, validBytes, 0.toByte())
                    outputBuffer = internalBuffer
                    try {
                        Thread.sleep(11) // 512 frames / 44100Hz = ~11.6ms pacing
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

            if (validBytes > 0 && !isPaused.get()) {
                val framesInChunk = validBytes / 4
                val presentationTimeUs = (totalFramesRead * 1_000_000L) / sampleRate
                totalFramesRead += framesInChunk

                onAudioDataAvailable(outputBuffer, validBytes, presentationTimeUs)
            }
        }
    }

    /**
     * Mixes two 16-bit Little-Endian PCM audio streams with clipping protection
     */
    private fun mixPcm16Stereo(src1: ByteArray, src2: ByteArray, dst: ByteArray, length: Int) {
        var i = 0
        while (i < length - 1) {
            val sample1 = (src1[i].toInt() and 0xFF) or (src1[i + 1].toInt() shl 8)
            val sample2 = (src2[i].toInt() and 0xFF) or (src2[i + 1].toInt() shl 8)

            // Convert to signed 16-bit
            val s1 = sample1.toShort()
            val s2 = sample2.toShort()

            val mixed = (s1 + s2).coerceIn(-32768, 32767)

            dst[i] = (mixed and 0xFF).toByte()
            dst[i + 1] = ((mixed shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun stop() {
        isCapturing.set(false)
        isPaused.set(false)

        try {
            captureThread?.interrupt()
            captureThread?.join(500)
        } catch (_: Exception) {}
        captureThread = null

        try {
            internalAudioRecord?.stop()
            internalAudioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing internal AudioRecord: ${e.message}")
        }
        internalAudioRecord = null

        try {
            micAudioRecord?.stop()
            micAudioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing mic AudioRecord: ${e.message}")
        }
        micAudioRecord = null
    }
}
