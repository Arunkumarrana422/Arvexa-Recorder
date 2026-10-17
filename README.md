# Arvexa Recorder 🎬🎮

**Arvexa Recorder** is a high-performance native Android screen recording application built with Kotlin, Jetpack Compose, Material 3, and MediaProjection. Designed specifically for mobile gamers, content creators, and BGMI gameplay capture with internal audio.

---

## 🚀 Key Features

- **🎮 Internal Game Audio Capture**: Direct game sound capture via Android 10+ `AudioPlaybackCaptureConfiguration` (BGMI & supported games) without microphone noise.
- **🎙️ Flexible Audio Modes**:
  1. Internal Game Audio
  2. Microphone Only
  3. Internal + Microphone (Dual-source PCM audio mixer)
  4. Mute (Silent video)
- **📹 Ultra HD Quality**: Up to 1080p Full HD at 60 FPS or 30 FPS.
- **⚡ Hardware Encoding**: H.264 AVC video encoding and AAC audio encoding muxed into standard MP4.
- **📁 Automatic MediaStore Gallery Sync**: Videos automatically save to `Movies/Arvexa Recorder` and appear instantly in phone's Gallery.
- **▶️ In-App Player**: Built-in Android Media3 ExoPlayer with seekbar, share sheet, rename, and delete options.
- **🛡️ 100% On-Device Privacy**: No videos or telemetry uploaded to any cloud server.

---

## 🛠️ GitHub Actions Release APK Build

This repository includes an automated GitHub Actions CI/CD workflow located at `.github/workflows/build-apk.yml`.

### How to Build Release APK on GitHub:
1. **Push your code to GitHub** from AI Studio (Top right settings / Push to GitHub).
2. Go to the **Actions** tab on your GitHub repository.
3. You will see the **Build & Release Android APK** workflow run automatically.
4. Once completed, click on the workflow run:
   - Under **Artifacts**, download `Arvexa-Recorder-Release-APK` (contains `Arvexa_Recorder_Release.apk`).
5. **Create a GitHub Release**:
   - Create a new release or tag (e.g., `v1.0.0`).
   - The workflow will automatically attach the signed `Arvexa_Recorder_Release.apk` directly to your GitHub Release!

---

## 📱 Local Build Instructions

```bash
# Clone the repository
git clone https://github.com/your-username/arvexa-recorder.git
cd arvexa-recorder

# Make gradlew executable
chmod +x gradlew

# Build Release APK
./gradlew assembleRelease

# The output APK will be in:
# app/build/outputs/apk/release/
```
