# Offline Music

An Android offline-first music player for audio files stored on the device.

## Features
- Reads local audio through Android MediaStore after permission is granted.
- Songs, albums, artists, search, and favorites.
- Background playback through Media3 MediaSessionService.
- Lock-screen and notification media controls supplied by Android Media3.
- No account, server, or network permission.

## Build
Open with Android Studio or run `gradle assembleDebug` with JDK 17 and Android SDK 35.
The GitHub Actions workflow builds a debug APK and uploads it as an artifact.
