# Sonora Music

Project status: Sonora Music settings, sorting, swipe navigation, and launcher icon updated.

**Store listing title suggestion:** Sonora Music: Music Player

An offline music player for Android. Browse and play music stored on your device, organize playlists, and enjoy your library with a clean, lightweight interface.

## Google Play listing draft

**Short description:** Offline music player to play songs, browse albums and artists, and create playlists.

**Search phrases to include naturally in the full description:** music player, offline music player, local music player, audio player, song playlists, music library, album browser, artist browser, lyrics.

> Search phrases can improve relevance, but Google Play decides search ranking. The title and description do not guarantee a particular position.

## Features
- Browse local songs, albums, and artists.
- Search the music library and manage favorites.
- Create playlists and add selected songs.
- Background playback through Media3 MediaSessionService.
- Lock-screen and notification media controls supplied by Android Media3.
- No account or server required.

## Build
Open with Android Studio or run `gradle assembleDebug` with JDK 17 and Android SDK 35.
The GitHub Actions workflow builds a debug APK and uploads it as an artifact.


UI layout fix: keep the Songs header and track count above the scrollable list; list now receives remaining vertical space.


Desktop visual refinement: Sonora vector mark is now used as the WPF window icon and sidebar brand, with refined hover/pressed button states and a consistent favorite-star control. Windows desktop theme styling is being refined.
