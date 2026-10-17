package com.example.model

enum class ResolutionOption(
    val label: String,
    val shortLabel: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val defaultBitrate: Int
) {
    RES_1080P("1080p Full HD (1920×1080)", "1080p", 1920, 1080, 12_000_000),
    RES_720P("720p HD (1280×720)", "720p", 1280, 720, 6_000_000),
    RES_480P("480p SD (854×480)", "480p", 854, 480, 3_000_000);

    companion object {
        fun fromLabel(label: String): ResolutionOption =
            entries.find { it.name == label || it.shortLabel == label } ?: RES_1080P
    }
}

enum class FpsOption(val fps: Int, val label: String) {
    FPS_60(60, "60 FPS (Ultra Smooth)"),
    FPS_30(30, "30 FPS (Standard)");

    companion object {
        fun fromValue(fps: Int): FpsOption =
            entries.find { it.fps == fps } ?: FPS_60
    }
}

enum class AudioSourceOption(val title: String, val subtitle: String) {
    INTERNAL_ONLY(
        "Internal Game Audio",
        "Direct capture for BGMI & supported games"
    ),
    MIC_ONLY(
        "Microphone Only",
        "Voice commentary & ambient audio"
    ),
    INTERNAL_AND_MIC(
        "Internal + Microphone",
        "Mix game audio with your voice chat"
    ),
    MUTE(
        "No Audio (Mute)",
        "Silent video recording"
    );

    fun requiresMicrophonePermission(): Boolean =
        this == MIC_ONLY || this == INTERNAL_AND_MIC

    fun requiresInternalAudioCapture(): Boolean =
        this == INTERNAL_ONLY || this == INTERNAL_AND_MIC
}

enum class CountdownOption(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    THREE_SEC(3, "3 Seconds"),
    FIVE_SEC(5, "5 Seconds")
}

data class RecordingConfig(
    val resolution: ResolutionOption = ResolutionOption.RES_1080P,
    val fps: FpsOption = FpsOption.FPS_60,
    val audioSource: AudioSourceOption = AudioSourceOption.INTERNAL_ONLY,
    val countdown: CountdownOption = CountdownOption.THREE_SEC,
    val customBitrate: Int? = null // When null, uses resolution default
) {
    val effectiveBitrate: Int
        get() = customBitrate ?: resolution.defaultBitrate
}
