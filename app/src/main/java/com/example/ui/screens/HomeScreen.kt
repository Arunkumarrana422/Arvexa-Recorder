package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.model.AudioSourceOption
import com.example.model.CountdownOption
import com.example.model.FpsOption
import com.example.model.RecordingConfig
import com.example.model.RecordingItem
import com.example.model.RecordingStatus
import com.example.model.ResolutionOption
import com.example.ui.MainViewModel
import com.example.ui.StorageInfo
import com.example.ui.theme.ArvexaBlue
import com.example.ui.theme.ArvexaCyan
import com.example.ui.theme.ArvexaIndigo
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyCardBorder
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DarkNavySurfaceVariant
import com.example.ui.theme.RecordRed
import com.example.ui.theme.RecordRedGlow
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestRecordPermission: () -> Unit,
    onNavigateToRecordings: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPlayRecording: (RecordingItem) -> Unit
) {
    val config by viewModel.config.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val recentRecordings by viewModel.recentRecordings.collectAsState()

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkNavyBackground,
                        Color(0xFF0F172A),
                        DarkNavyBackground
                    )
                )
            )
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Top Bar
        item {
            Spacer(modifier = Modifier.height(16.dp))
            HomeHeader(onNavigateToSettings = onNavigateToSettings)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Storage Info Pill
        item {
            StorageIndicatorCard(storageInfo = storageInfo)
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Main Recording Controller (Hero)
        item {
            HeroRecordController(
                state = recordingState,
                onStartClick = onRequestRecordPermission,
                onPauseClick = { viewModel.pauseRecording() },
                onResumeClick = { viewModel.resumeRecording() },
                onStopClick = { viewModel.stopRecording() }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Quick Recording Options Card
        item {
            RecordingConfigurationSection(
                config = config,
                isRecording = recordingState is RecordingStatus.Recording || recordingState is RecordingStatus.Countdown,
                onResolutionSelected = { viewModel.updateResolution(it) },
                onFpsSelected = { viewModel.updateFps(it) },
                onAudioSourceSelected = { viewModel.updateAudioSource(it) },
                onCountdownSelected = { viewModel.updateCountdown(it) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Recent Recordings Section
        item {
            RecentRecordingsSection(
                recentList = recentRecordings,
                onViewAll = onNavigateToRecordings,
                onItemClick = onPlayRecording
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun HomeHeader(onNavigateToSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(ArvexaBlue, ArvexaIndigo)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Arvexa Recorder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "High-FPS Game & Internal Audio Recorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArvexaCyan
                )
            }
        }

        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .size(44.dp)
                .background(DarkNavyCard, CircleShape)
                .testTag("settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }
    }
}

@Composable
fun StorageIndicatorCard(storageInfo: StorageInfo) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = ArvexaCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Shared Storage",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = storageInfo.availableFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ArvexaBlue.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "~${storageInfo.estimatedMinutesRemaining} min left",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ArvexaBlue
                )
            }
        }
    }
}

@Composable
fun HeroRecordController(
    state: RecordingStatus,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (state is RecordingStatus.Recording) RecordRed.copy(alpha = 0.5f) else DarkNavyCardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is RecordingStatus.Idle -> {
                    // Start Button
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(ArvexaBlue, ArvexaIndigo, DarkNavyCard)
                                )
                            )
                            .border(3.dp, ArvexaCyan.copy(alpha = 0.6f), CircleShape)
                            .clickable(onClick = onStartClick)
                            .testTag("start_recording_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(RecordRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Recording",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Ready to Record",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap to start capturing your screen & game audio",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                is RecordingStatus.Countdown -> {
                    // Animated Countdown
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(ArvexaIndigo.copy(alpha = 0.3f))
                            .border(4.dp, ArvexaCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${state.remainingSeconds}",
                            fontSize = 60.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArvexaCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Recording starts in ${state.remainingSeconds}s…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                is RecordingStatus.Recording -> {
                    val totalSec = state.durationMs / 1000
                    val mm = totalSec / 60
                    val ss = totalSec % 60
                    val timeFormatted = String.format("%02d:%02d", mm, ss)

                    // Active Recording Pulse Circle
                    Box(
                        modifier = Modifier
                            .scale(if (!state.isPaused) pulseScale else 1f)
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isPaused) WarningAmber.copy(alpha = 0.2f)
                                else RecordRed.copy(alpha = 0.2f)
                            )
                            .border(
                                3.dp,
                                if (state.isPaused) WarningAmber else RecordRed,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isPaused) WarningAmber else RecordRed)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = timeFormatted,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (state.isPaused) "Recording Paused" else "REC • Live Recording",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isPaused) WarningAmber else RecordRed
                    )

                    // Audio Mode Status Badge
                    if (state.internalAudioActive) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SuccessGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BGMI Game Audio Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    state.internalAudioWarning?.let { warning ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Live Controls: Pause/Resume and Stop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                if (state.isPaused) onResumeClick() else onPauseClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isPaused) SuccessGreen else DarkNavyCard
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.isPaused) "Resume" else "Pause")
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = onStopClick,
                            colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_recording_button")
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is RecordingStatus.Finalizing -> {
                    CircularProgressIndicator(
                        color = ArvexaBlue,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Finalizing Video…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Muxing MP4 and saving to Movies/Arvexa Recorder",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                is RecordingStatus.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = RecordRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Recording Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RecordRed
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onStartClick) {
                        Text("Try Again")
                    }
                }

                is RecordingStatus.Saved -> {
                    // Handled by RecordingSavedDialog
                }
            }
        }
    }
}

@Composable
fun RecordingConfigurationSection(
    config: RecordingConfig,
    isRecording: Boolean,
    onResolutionSelected: (ResolutionOption) -> Unit,
    onFpsSelected: (FpsOption) -> Unit,
    onAudioSourceSelected: (AudioSourceOption) -> Unit,
    onCountdownSelected: (CountdownOption) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Recording Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Resolution Selector
            Text(
                text = "Resolution",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionOption.entries.forEach { res ->
                    val isSelected = config.resolution == res
                    ChipOption(
                        label = res.shortLabel,
                        isSelected = isSelected,
                        enabled = !isRecording,
                        modifier = Modifier.weight(1f),
                        onClick = { onResolutionSelected(res) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. FPS Selector
            Text(
                text = "Frame Rate (FPS)",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FpsOption.entries.forEach { fpsOpt ->
                    val isSelected = config.fps == fpsOpt
                    ChipOption(
                        label = "${fpsOpt.fps} FPS",
                        isSelected = isSelected,
                        enabled = !isRecording,
                        modifier = Modifier.weight(1f),
                        onClick = { onFpsSelected(fpsOpt) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Audio Source Selector
            Text(
                text = "Audio Source (BGMI Priority)",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AudioSourceOption.entries.forEach { audioOpt ->
                    val isSelected = config.audioSource == audioOpt
                    AudioSourceRow(
                        option = audioOpt,
                        isSelected = isSelected,
                        enabled = !isRecording,
                        onClick = { onAudioSourceSelected(audioOpt) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Countdown Selector
            Text(
                text = "Countdown Timer",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CountdownOption.entries.forEach { countOpt ->
                    val isSelected = config.countdown == countOpt
                    ChipOption(
                        label = countOpt.label,
                        isSelected = isSelected,
                        enabled = !isRecording,
                        modifier = Modifier.weight(1f),
                        onClick = { onCountdownSelected(countOpt) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChipOption(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) ArvexaBlue else DarkNavyCard
            )
            .border(
                1.dp,
                if (isSelected) ArvexaCyan else DarkNavyCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else TextSecondary
        )
    }
}

@Composable
fun AudioSourceRow(
    option: AudioSourceOption,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val icon = when (option) {
        AudioSourceOption.INTERNAL_ONLY -> Icons.Default.Headphones
        AudioSourceOption.MIC_ONLY -> Icons.Default.Mic
        AudioSourceOption.INTERNAL_AND_MIC -> Icons.AutoMirrored.Filled.VolumeUp
        AudioSourceOption.MUTE -> Icons.Default.MicOff
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) ArvexaBlue.copy(alpha = 0.15f) else DarkNavyCard
            )
            .border(
                1.dp,
                if (isSelected) ArvexaBlue else DarkNavyCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) ArvexaCyan else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) TextPrimary else TextSecondary
            )
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ArvexaCyan)
            )
        }
    }
}

@Composable
fun RecentRecordingsSection(
    recentList: List<RecordingItem>,
    onViewAll: () -> Unit,
    onItemClick: (RecordingItem) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Recordings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            TextButton(onClick = onViewAll) {
                Text("View All (${recentList.size})", color = ArvexaCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (recentList.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No recordings yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Your screen & BGMI gameplay captures will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recentList, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyCardBorder),
                        modifier = Modifier
                            .width(180.dp)
                            .clickable { onItemClick(item) }
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(Uri.parse(item.uriString))
                                        .videoFrameMillis(1000)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Play icon badge
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                // Duration pill
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.formattedDuration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.resolution} • ${item.formattedSize}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
