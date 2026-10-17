package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.model.RecordingItem
import com.example.model.RecordingStatus
import com.example.ui.theme.ArvexaBlue
import com.example.ui.theme.ArvexaCyan
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyCardBorder
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.RecordRed
import com.example.ui.theme.SuccessGreen

@Composable
fun RecordingSavedDialog(
    saved: RecordingStatus.Saved,
    onDismiss: () -> Unit,
    onPlay: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    onRename: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit
) {
    val context = LocalContext.current

    val item = RecordingItem(
        id = 0L,
        title = saved.filename,
        uriString = saved.uri,
        filePath = saved.filePath,
        durationMs = saved.durationMs,
        resolution = saved.resolution,
        fps = 60,
        audioSource = "Internal / Mic",
        fileSizeBytes = saved.sizeBytes,
        dateAdded = System.currentTimeMillis()
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("saved_recording_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = SuccessGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Recording Saved!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Saved to Gallery in Movies/Arvexa Recorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Video Card Preview
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Video Thumbnail
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(Uri.parse(saved.uri))
                                    .videoFrameMillis(1000)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Video Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = saved.filename,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${saved.resolution} • ${item.formattedDuration} • ${item.formattedSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Play Button
                Button(
                    onClick = {
                        onDismiss()
                        onPlay(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArvexaBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_play_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Recording", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Row: Share, Rename, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        icon = Icons.Default.Share,
                        label = "Share",
                        tint = ArvexaCyan,
                        onClick = { onShare(item) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_share_button")
                    )

                    ActionTile(
                        icon = Icons.Default.DriveFileRenameOutline,
                        label = "Rename",
                        tint = Color.White,
                        onClick = { onRename(item) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_rename_button")
                    )

                    ActionTile(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = RecordRed,
                        onClick = { onDelete(item) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_delete_button")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = DarkNavyCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyCardBorder),
        modifier = modifier.height(58.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}
