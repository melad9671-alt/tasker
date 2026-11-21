# Sonora Music for Windows

Native Windows desktop companion for the Sonora Music Android app.

## Build
Requires Windows 10/11 and .NET 8 SDK.

```powershell
dotnet publish Sonora.Desktop.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o publish
```

The Windows CI workflow publishes a self-contained x64 build artifact. The first milestone includes local folder scanning, search, audio playback, playback progress, previous/next, favorites, and library sections. Playlist editing, richer metadata/artwork extraction, lyrics, and persisted preferences are planned for the next milestone.
