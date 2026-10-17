package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ArvexaDatabase
import com.example.data.RecordingEntity
import com.example.model.AudioSourceOption
import com.example.model.CountdownOption
import com.example.model.FpsOption
import com.example.model.RecordingConfig
import com.example.model.RecordingItem
import com.example.model.ResolutionOption
import com.example.recording.RecordingStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Arvexa Recorder", appName)
    }

    @Test
    fun `test filename generation format`() {
        val filename = RecordingStorageManager.generateFilename()
        assertTrue(filename.startsWith("Arvexa_Recording_"))
        assertTrue(filename.endsWith(".mp4"))
    }

    @Test
    fun `test recording item formatting`() {
        val item = RecordingItem(
            id = 1L,
            title = "Arvexa_Recording_test.mp4",
            uriString = "content://media/external/video/media/1",
            filePath = "Movies/Arvexa Recorder/Arvexa_Recording_test.mp4",
            durationMs = 125000L, // 2 min 5 sec
            resolution = "1920×1080",
            fps = 60,
            audioSource = "Internal Game Audio",
            fileSizeBytes = 52428800L, // 50 MB
            dateAdded = System.currentTimeMillis()
        )

        assertEquals("02:05", item.formattedDuration)
        assertEquals("50.0 MB", item.formattedSize)
    }

    @Test
    fun `test recording config defaults and audio permission requirements`() {
        val config = RecordingConfig()
        assertEquals(ResolutionOption.RES_1080P, config.resolution)
        assertEquals(FpsOption.FPS_60, config.fps)
        assertEquals(AudioSourceOption.INTERNAL_ONLY, config.audioSource)
        assertEquals(CountdownOption.THREE_SEC, config.countdown)
        assertEquals(12_000_000, config.effectiveBitrate)

        assertTrue(AudioSourceOption.MIC_ONLY.requiresMicrophonePermission())
        assertTrue(AudioSourceOption.INTERNAL_AND_MIC.requiresMicrophonePermission())
        assertTrue(AudioSourceOption.INTERNAL_ONLY.requiresInternalAudioCapture())
    }

    @Test
    fun `test database insertion and query`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ArvexaDatabase.getInstance(context)
        val dao = db.recordingDao()

        val entity = RecordingEntity(
            title = "Test_Recording.mp4",
            uriString = "content://test/1",
            filePath = "Movies/Arvexa Recorder/Test_Recording.mp4",
            durationMs = 60000L,
            resolution = "1080×1920",
            fps = 60,
            audioSource = "Internal",
            fileSizeBytes = 10485760L,
            dateAdded = System.currentTimeMillis()
        )

        val insertedId = dao.insertRecording(entity)
        assertTrue(insertedId > 0)

        val fetched = dao.getRecordingById(insertedId)
        assertNotNull(fetched)
        assertEquals("Test_Recording.mp4", fetched?.title)

        val all = dao.getAllRecordings().first()
        assertTrue(all.any { it.title == "Test_Recording.mp4" })
    }
}
