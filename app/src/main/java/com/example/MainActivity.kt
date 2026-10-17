package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.model.RecordingItem
import com.example.ui.MainViewModel
import com.example.ui.components.RecordingSavedDialog
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecordingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ArvexaTheme
import com.example.ui.theme.DarkNavyBackground
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    RECORDINGS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ArvexaTheme {
                MainAppContent(viewModel = viewModel, activity = this)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel, activity: ComponentActivity) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val config by viewModel.config.collectAsState()
    val activePlaybackItem by viewModel.activePlaybackItem.collectAsState()
    val savedVideoDialog by viewModel.savedVideoDialog.collectAsState()
    val userFeedback by viewModel.userFeedbackMessage.collectAsState()

    LaunchedEffect(userFeedback) {
        userFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    // MediaProjection screen capture consent launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startRecording(result.resultCode, result.data!!)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Screen recording permission was cancelled.")
            }
        }
    }

    // Microphone permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Launch screen capture intent
            val mpManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is needed to record voice audio.")
            }
        }
    }

    // Notification permission launcher for Android 13+
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with start
        checkMicAndStartScreenCapture(
            activity = activity,
            requiresMic = config.audioSource.requiresMicrophonePermission(),
            onHasPermissions = {
                val mpManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
            },
            onRequestMic = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )
    }

    val startRecordingFlow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkMicAndStartScreenCapture(
                activity = activity,
                requiresMic = config.audioSource.requiresMicrophonePermission(),
                onHasPermissions = {
                    val mpManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                },
                onRequestMic = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }
    }

    val shareVideo: (RecordingItem) -> Unit = { item ->
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                putExtra(Intent.EXTRA_SUBJECT, item.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(shareIntent, "Share recording via"))
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to share video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val openInGallery: (RecordingItem) -> Unit = { item ->
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(item.uriString), "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(activity, "No compatible video player found.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = DarkNavyBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onRequestRecordPermission = startRecordingFlow,
                    onNavigateToRecordings = { currentScreen = Screen.RECORDINGS },
                    onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                    onPlayRecording = { item -> viewModel.playVideo(item) }
                )

                Screen.RECORDINGS -> RecordingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.HOME },
                    onPlay = { item -> viewModel.playVideo(item) },
                    onShare = shareVideo,
                    onOpenExternal = openInGallery
                )

                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.HOME }
                )
            }

            // In-App Video Player Dialog
            activePlaybackItem?.let { item ->
                VideoPlayerDialog(
                    item = item,
                    onDismiss = { viewModel.dismissVideoPlayer() },
                    onShare = shareVideo,
                    onOpenExternal = openInGallery
                )
            }

            // Saved Recording Success Dialog
            savedVideoDialog?.let { saved ->
                RecordingSavedDialog(
                    saved = saved,
                    onDismiss = { viewModel.dismissSavedDialog() },
                    onPlay = { item ->
                        viewModel.dismissSavedDialog()
                        viewModel.playVideo(item)
                    },
                    onShare = { item -> shareVideo(item) },
                    onRename = { item ->
                        viewModel.dismissSavedDialog()
                        // Open library to rename
                        currentScreen = Screen.RECORDINGS
                    },
                    onDelete = { item ->
                        viewModel.dismissSavedDialog()
                        viewModel.deleteRecording(item)
                    }
                )
            }
        }
    }
}

private fun checkMicAndStartScreenCapture(
    activity: ComponentActivity,
    requiresMic: Boolean,
    onHasPermissions: () -> Unit,
    onRequestMic: () -> Unit
) {
    if (requiresMic) {
        val hasMic = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            onHasPermissions()
        } else {
            onRequestMic()
        }
    } else {
        // Internal audio alone or mute
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // AudioPlaybackCaptureConfiguration requires RECORD_AUDIO permission at runtime in Android 10+
        if (hasAudioPerm) {
            onHasPermissions()
        } else {
            onRequestMic()
        }
    }
}
