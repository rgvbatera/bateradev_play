# Batera Dev Play

Batera Dev Play is an Android app for drummers to study, organize repertoire, and practice with audio-focused tools. The app brings together reference downloads, stem separation, AI-assisted music generation, setlist organization, file management, and a practice screen designed for music study.

## Screenshots

<p align="center">
  <img src="image/photo_1_2026-05-13_22-29-13.jpg" width="220" alt="Batera Dev Play screenshot 1" />
  <img src="image/photo_2_2026-05-13_22-29-13.jpg" width="220" alt="Batera Dev Play screenshot 2" />
  <img src="image/photo_3_2026-05-13_22-29-13.jpg" width="220" alt="Batera Dev Play screenshot 3" />
</p>

<p align="center">
  <img src="image/photo_4_2026-05-13_22-29-13.jpg" width="220" alt="Batera Dev Play screenshot 4" />
  <img src="image/photo_5_2026-05-13_22-29-13.jpg" width="220" alt="Batera Dev Play screenshot 5" />
  <img src="image/photo_6_2026-05-13_22-29-13.jpg" width="220" alt="Batera Dev Play screenshot 6" />
</p>

## Features

- Download audio from links to create study material.
- Separate stems to isolate or remove parts of a track, with a focus on drums.
- Generate music and backing tracks with AI through an external backend.
- Practice screen with controls designed for music study.
- Organize songs into setlists.
- Manage downloaded and processed files.
- Analyze audio to support tempo, structure, and performance study.

## How It Works

The Android app is the main interface. Some features, such as downloads, stem separation, audio analysis, and AI generation, depend on a separately configured backend API.

This public repository does not include the server, credentials, tokens, or private configuration. The backend URL must be provided at build time through the `BACKEND_BASE_URL` property.

## Backend Configuration

To run on the Android emulator using a local backend on the host machine:

```powershell
.\gradlew assembleDebug -PBACKEND_BASE_URL=http://10.0.2.2:5000
```

To run on a physical device, use the address of a server reachable from the device network:

```powershell
.\gradlew assembleDebug -PBACKEND_BASE_URL=http://YOUR_SERVER:5000
```

The `.env.example` file documents the expected variable:

```env
BACKEND_BASE_URL=http://10.0.2.2:5000
```

## Tech Stack

- Kotlin
- Android Jetpack Compose
- Material 3
- Navigation Compose
- OkHttp
- Retrofit
- Coil
- DataStore
- Media3 ExoPlayer
- Gradle Kotlin DSL

## Project Structure

```text
app/
  src/main/java/com/example/bateradev_play/
    audio/          audio engines and metronome
    data/           models, repositories, and API services
    services/       Android services
    ui/             screens, navigation, theme, and viewmodels
image/              app screenshots
gradle/             wrapper and version configuration
```

## Note

This repository contains only the Android app. The backend must be implemented, hosted, and configured separately according to the target environment.
