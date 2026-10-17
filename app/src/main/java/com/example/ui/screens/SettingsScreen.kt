package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioSourceOption
import com.example.model.CountdownOption
import com.example.model.FpsOption
import com.example.model.ResolutionOption
import com.example.recording.RecordingStorageManager
import com.example.ui.MainViewModel
import com.example.ui.theme.ArvexaBlue
import com.example.ui.theme.ArvexaCyan
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyCardBorder
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DarkNavySurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val config by viewModel.config.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()

    var bitrateMbps by remember(config.effectiveBitrate) {
        mutableFloatStateOf(config.effectiveBitrate / 1_000_000f)
    }

    Scaffold(
        containerColor = DarkNavyBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(DarkNavyCard, CircleShape)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Encoding, audio & storage options",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Video Encoding Settings Card
            item {
                SettingsSectionCard(title = "Video Encoding Quality", icon = Icons.Default.Tune) {
                    // Resolution
                    Text(
                        text = "Default Resolution",
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
                                enabled = true,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.updateResolution(res)
                                    bitrateMbps = res.defaultBitrate / 1_000_000f
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Frame Rate
                    Text(
                        text = "Target Frame Rate",
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
                                enabled = true,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.updateFps(fpsOpt) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Bitrate Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Video Bitrate",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "${bitrateMbps.toInt()} Mbps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ArvexaCyan
                        )
                    }
                    Slider(
                        value = bitrateMbps,
                        onValueChange = {
                            bitrateMbps = it
                            viewModel.updateCustomBitrate((it * 1_000_000).toInt())
                        },
                        valueRange = 2f..24f,
                        steps = 21,
                        colors = SliderDefaults.colors(
                            thumbColor = ArvexaCyan,
                            activeTrackColor = ArvexaBlue
                        )
                    )
                    Text(
                        text = "Recommended: 1080p (8–16 Mbps), 720p (4–8 Mbps), 480p (2–4 Mbps)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Audio & BGMI Gameplay Info Card
            item {
                SettingsSectionCard(title = "Audio & Game Capture", icon = Icons.Default.GraphicEq) {
                    Text(
                        text = "Default Audio Source",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioSourceOption.entries.forEach { opt ->
                            AudioSourceRow(
                                option = opt,
                                isSelected = config.audioSource == opt,
                                enabled = true,
                                onClick = { viewModel.updateAudioSource(opt) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkNavyCardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // BGMI Support Guide
                    Text(
                        text = "BGMI Gameplay Internal Audio Notes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ArvexaCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Android 10+ allows direct internal audio capture via AudioPlaybackCaptureConfiguration.\n" +
                               "• BGMI gameplay audio is recorded in crisp stereo PCM at 44.1 kHz.\n" +
                               "• If in-game voice chat restricts capture on specific device firmware, select 'Internal + Microphone' to mix in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // Countdown & Automation
            item {
                SettingsSectionCard(title = "Countdown & Timing", icon = Icons.Default.Videocam) {
                    Text(
                        text = "Countdown Before Recording Starts",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountdownOption.entries.forEach { countOpt ->
                            ChipOption(
                                label = countOpt.label,
                                isSelected = config.countdown == countOpt,
                                enabled = true,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.updateCountdown(countOpt) }
                            )
                        }
                    }
                }
            }

            // Storage & Directory Info
            item {
                SettingsSectionCard(title = "Storage & Save Location", icon = Icons.Default.Folder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ArvexaCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Save Destination",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Internal Storage / ${RecordingStorageManager.RELATIVE_PATH}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All finalized videos are indexed in Android MediaStore so they immediately appear in the Gallery and Google Photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Available Space: ${storageInfo.availableFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Privacy & About
            item {
                SettingsSectionCard(title = "Privacy & App Info", icon = Icons.Default.Security) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "100% On-Device & Private",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "No video or audio data is ever uploaded to any server or cloud.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Arvexa Recorder v1.0.0 (Build 20261017)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ArvexaCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
