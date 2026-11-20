package com.melad.offlinemusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import com.google.common.util.concurrent.ListenableFuture

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val artworkUri: Uri?,
    val artworkData: ByteArray? = null,
    val lyrics: String? = null
)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            setContent { OfflineMusicApp(controller) }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}

private fun artistArtworkFile(context: Context, artist: String): File =
    File(File(context.filesDir, "artist_artwork"), "${artist.trim().lowercase().hashCode()}.jpg")

private fun fetchArtistArtwork(context: Context, artist: String): Boolean {
    val target = artistArtworkFile(context, artist)
    if (target.exists() && target.length() > 0L) return true
    target.parentFile?.mkdirs()
    val query = URLEncoder.encode("${artist} singer portrait", "UTF-8")
    val api = URL("https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$query&gsrnamespace=6&gsrlimit=3&prop=imageinfo&iiprop=url&iiurlwidth=360&format=json")
    val connection = (api.openConnection() as HttpURLConnection).apply {
        connectTimeout = 4500
        readTimeout = 5500
        setRequestProperty("User-Agent", "OfflineMusicAndroid/1.0 (artist artwork lookup)")
    }
    try {
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val pages = org.json.JSONObject(response).optJSONObject("query")?.optJSONObject("pages") ?: return false
        var imageUrl: String? = null
        val keys = pages.keys()
        while (keys.hasNext() && imageUrl == null) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0)
            imageUrl = info?.optString("thumburl")?.takeIf { it.startsWith("https://") }
                ?: info?.optString("url")?.takeIf { it.startsWith("https://") }
        }
        val selectedUrl = imageUrl ?: return false
        val imageConnection = (URL(selectedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4500
            readTimeout = 5500
            setRequestProperty("User-Agent", "OfflineMusicAndroid/1.0")
        }
        try {
            imageConnection.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return false
                target.writeBytes(bytes)
                return true
            }
        } finally {
            imageConnection.disconnect()
        }
    } catch (_: Exception) {
        return false
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun ArtistArtwork(artist: String, version: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, artist, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = artistArtworkFile(context, artist)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF352222)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, contentDescription = "$artist artist photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(27.dp))
    }
}


private fun readTrackLyrics(context: Context, file: DocumentFile): String? {
    if (file.name.orEmpty().endsWith(".mp3", true)) {
        val embedded = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size < 10 || bytes[0] != 73.toByte() || bytes[1] != 68.toByte() || bytes[2] != 51.toByte()) return@use null
                val version = bytes[3].toInt() and 0xff
                if (version !in 3..4) return@use null
                val tagSize = ((bytes[6].toInt() and 0x7f) shl 21) or ((bytes[7].toInt() and 0x7f) shl 14) or ((bytes[8].toInt() and 0x7f) shl 7) or (bytes[9].toInt() and 0x7f)
                var offset = 10
                val end = minOf(bytes.size, 10 + tagSize)
                while (offset + 10 <= end) {
                    val id = String(bytes, offset, 4, Charsets.ISO_8859_1)
                    if (id.all { it == 0.toChar() }) break
                    val size = if (version == 4) {
                        ((bytes[offset+4].toInt() and 0x7f) shl 21) or ((bytes[offset+5].toInt() and 0x7f) shl 14) or ((bytes[offset+6].toInt() and 0x7f) shl 7) or (bytes[offset+7].toInt() and 0x7f)
                    } else {
                        ((bytes[offset+4].toInt() and 0xff) shl 24) or ((bytes[offset+5].toInt() and 0xff) shl 16) or ((bytes[offset+6].toInt() and 0xff) shl 8) or (bytes[offset+7].toInt() and 0xff)
                    }
                    val start = offset + 10
                    val finish = (start + size).coerceAtMost(end)
                    if (id == "USLT" && finish > start + 4) {
                        val encoding = bytes[start].toInt() and 0xff
                        val body = start + 4
                        val terminator = if (encoding == 1 || encoding == 2) {
                            var p = body
                            while (p + 1 < finish && !(bytes[p] == 0.toByte() && bytes[p+1] == 0.toByte())) p += 2
                            p + 2
                        } else {
                            var p = body
                            while (p < finish && bytes[p] != 0.toByte()) p++
                            p + 1
                        }
                        val charset = when (encoding) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
                        return@use String(bytes, terminator.coerceAtMost(finish), (finish - terminator).coerceAtLeast(0), charset).trim().takeIf { it.isNotBlank() }
                    }
                    if (size <= 0) break
                    offset = finish
                }
                null
            }
        }.getOrNull()
        if (!embedded.isNullOrBlank()) return embedded
    }
    return null
}

private fun normalizeLyricsName(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

private fun readTextFile(context: Context, uri: Uri): String? = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
    when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, Charsets.UTF_16)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, Charsets.UTF_16)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        else -> {
            val utf8 = String(bytes, Charsets.UTF_8)
            if ('\uFFFD' in utf8) String(bytes, java.nio.charset.Charset.forName("windows-1256")) else utf8
        }
    }.trim()
}.getOrNull()

private fun findMatchingLyrics(
    context: Context,
    rootUri: Uri,
    targetUri: Uri,
    title: String,
    artist: String
): String? {
    val root = runCatching { DocumentFile.fromTreeUri(context, rootUri) }.getOrNull() ?: return null
    val titleKey = normalizeLyricsName(title)
    val artistKey = normalizeLyricsName(artist)
    val audioName = runCatching {
        findTrackDocument(context, rootUri, targetUri)?.name.orEmpty().substringBeforeLast('.')
    }.getOrDefault("")
    val audioKey = normalizeLyricsName(audioName)
    val accepted = setOf(
        titleKey, audioKey, artistKey + titleKey, titleKey + artistKey,
        normalizeLyricsName("$artist - $title"), normalizeLyricsName("$title - $artist")
    ).filter { it.isNotBlank() }.toSet()

    val pending = ArrayDeque<DocumentFile>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val folder = pending.removeFirst()
        val children = runCatching { folder.listFiles().toList() }.getOrDefault(emptyList())
        if (children.any { it.uri == targetUri }) {
            val match = children.firstOrNull { candidate ->
                if (!candidate.isFile) return@firstOrNull false
                val name = candidate.name.orEmpty()
                val extension = name.substringAfterLast('.', "").lowercase()
                extension in setOf("lrc", "txt") &&
                    normalizeLyricsName(name.substringBeforeLast('.', name)) in accepted
            }
            if (match != null) return readTextFile(context, match.uri)?.takeIf { it.isNotBlank() }
        }
        children.filter { it.isDirectory }.forEach { pending.add(it) }
    }
    return null
}

private fun parseLyricsLines(raw: String): List<Pair<Long?, String>> =
    raw.lineSequence().flatMap { line ->
        val timestamps = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]").findAll(line).toList()
        if (timestamps.isEmpty()) {
            val clean = line.trim()
            if (clean.isBlank() || Regex("^\\[[^\\]]+:[^\\]]*]$").matches(clean)) emptyList()
            else listOf(null to clean)
        } else {
            val lyricText = line.substringAfterLast(']').trim()
            if (lyricText.isBlank()) emptyList()
            else timestamps.map { match ->
                val millis = match.groupValues[1].toLong() * 60000L +
                    match.groupValues[2].toLong() * 1000L +
                    (match.groupValues[3].padEnd(3, '0').take(3).toIntOrNull() ?: 0)
                millis to lyricText
            }
        }
    }.toList()

private fun findTrackDocument(context: Context, rootUri: Uri, targetUri: Uri): DocumentFile? {
    val root = runCatching { DocumentFile.fromTreeUri(context, rootUri) }.getOrNull() ?: return null
    val pending = ArrayDeque<DocumentFile>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val folder = pending.removeFirst()
        val children = runCatching { folder.listFiles().toList() }.getOrDefault(emptyList())
        for (child in children) {
            if (child.uri == targetUri) return child
            if (child.isDirectory) pending.add(child)
        }
    }
    return null
}

private suspend fun loadTracks(
    context: Context,
    treeUri: Uri,
    existing: List<Track> = emptyList(),
    onBatch: suspend (List<Track>) -> Unit = {}
): List<Track> = withContext(Dispatchers.IO) {
    val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
        ?: return@withContext existing

    val allFiles = mutableListOf<DocumentFile>()
    val pending = ArrayDeque<DocumentFile>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val folder = pending.removeFirst()
        runCatching { folder.listFiles().toList() }.getOrDefault(emptyList()).forEach { file ->
            if (file.isDirectory) pending.add(file) else allFiles.add(file)
        }
    }

    val audioExtensions = listOf(".mp3", ".m4a", ".m4b", ".aac", ".flac", ".ogg", ".opus", ".wav", ".wma", ".aiff", ".aif", ".3gp", ".amr")
    val audioFiles = allFiles.filter { file ->
        val name = file.name.orEmpty().lowercase()
        audioExtensions.any { name.endsWith(it) } || file.type?.startsWith("audio/", ignoreCase = true) == true
    }.sortedBy { it.name.orEmpty().lowercase() }

    val existingByUri = existing.associateBy { it.uri.toString() }
    val discoveredUris = audioFiles.map { it.uri.toString() }.toSet()
    val retained = existing.filter { discoveredUris.contains(it.uri.toString()) }
    val added = mutableListOf<Track>()
    val batch = mutableListOf<Track>()

    for (file in audioFiles) {
        if (existingByUri.containsKey(file.uri.toString())) continue

        val fallbackTitle = file.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Unknown title"
        val retriever = MediaMetadataRetriever()
        val opened = runCatching { retriever.setDataSource(context, file.uri); true }.getOrDefault(false)
        fun metadata(key: Int): String? =
            if (opened) runCatching { retriever.extractMetadata(key)?.takeIf { it.isNotBlank() } }.getOrNull() else null

        val title = metadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallbackTitle
        val artist = metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown artist"
        val album = metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown album"
        val duration = metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val artworkData = if (opened) runCatching { retriever.embeddedPicture }.getOrNull() else null
        runCatching { retriever.release() }

        // Lyrics are loaded only when the user opens the lyrics screen.
        // Reading every complete MP3 during library scanning made first launch unnecessarily slow.
        val id = file.uri.toString().hashCode().toLong()
        val track = Track(id, title, artist, album, duration, file.uri, null, artworkData, null)
        added.add(track)
        batch.add(track)

        // Publish small batches so the list grows progressively without flooding weak devices
        // with hundreds of Compose state updates in one burst.
        if (added.size == 1 || batch.size >= 4) {
            onBatch(batch.toList())
            batch.clear()
        }
    }

    if (batch.isNotEmpty()) onBatch(batch.toList())
    (retained + added).sortedBy { it.title.lowercase() }
}

private fun saveTrackCache(context: Context, folderUri: Uri, tracks: List<Track>) {
    val artDir = java.io.File(context.filesDir, "artwork_cache").apply { mkdirs() }
    val json = org.json.JSONArray()
    tracks.forEach { track ->
        val artFile = java.io.File(artDir, "${track.id}.img")
        if (track.artworkData != null) runCatching { artFile.writeBytes(track.artworkData) }
        else if (artFile.exists()) runCatching { artFile.delete() }
        json.put(org.json.JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("duration", track.duration)
            put("uri", track.uri.toString())
            put("hasArtwork", track.artworkData != null && artFile.exists())
            put("lyrics", track.lyrics ?: "")
        })
    }
    context.getSharedPreferences("offline_music", Context.MODE_PRIVATE).edit()
        .putString("cached_folder_uri", folderUri.toString())
        .putString("cached_tracks", json.toString())
        .apply()
}

private fun loadTrackCache(context: Context, folderUri: Uri): List<Track>? {
    val prefs = context.getSharedPreferences("offline_music", Context.MODE_PRIVATE)
    if (prefs.getString("cached_folder_uri", null) != folderUri.toString()) return null
    val raw = prefs.getString("cached_tracks", null) ?: return null
    return runCatching {
        val array = org.json.JSONArray(raw)
        val artDir = java.io.File(context.filesDir, "artwork_cache")
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val uri = Uri.parse(item.getString("uri"))
            val id = item.getLong("id")
            val artFile = java.io.File(artDir, "${id}.img")
            val art = if (item.optBoolean("hasArtwork") && artFile.exists()) runCatching { artFile.readBytes() }.getOrNull() else null
            Track(id, item.getString("title"), item.getString("artist"), item.getString("album"), item.optLong("duration"), uri, null, art, item.optString("lyrics").takeIf { it.isNotBlank() })
        }
    }.getOrNull()
}
private fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setArtworkData(artworkData, null)
                .build()
        )
        .build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineMusicApp(controller: MediaController?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("offline_music", Context.MODE_PRIVATE) }
    var folderUri by remember { mutableStateOf(prefs.getString("music_folder_uri", null)?.let(Uri::parse)) }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var scanning by remember { mutableStateOf(false) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var artistImagesVersion by remember { mutableIntStateOf(0) }
    var artistFetchState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var tab by remember { mutableStateOf("Songs") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var currentId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showPlayerMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var lyricsImportError by remember { mutableStateOf<String?>(null) }
    var songMenuTrack by remember { mutableStateOf<Track?>(null) }
    var songMenuExpanded by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var trackToAdd by remember { mutableStateOf<Track?>(null) }
    var playlistRefresh by remember { mutableIntStateOf(0) }
    var playlistName by remember { mutableStateOf("") }
    var playlistNames by remember { mutableStateOf(prefs.getStringSet("playlist_names", emptySet())?.toSet() ?: emptySet()) }
    var recentIds by remember { mutableStateOf(prefs.getString("recent_ids", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }
    var splashVisible by remember { mutableStateOf(true) }
    val songsListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        delay(2450)
        splashVisible = false
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            folderUri = uri
            prefs.edit().putString("music_folder_uri", uri.toString()).apply()
            tab = "Songs"
            selectedGroup = null
        }
    }

    LaunchedEffect(folderUri) {
        val selectedUri = folderUri
        if (selectedUri == null) {
            tracks = emptyList()
            scanning = false
        } else {
            val cached = withContext(Dispatchers.IO) { loadTrackCache(context, selectedUri) }
            if (cached != null) {
                tracks = cached
                scanning = false
            } else {
                scanning = true
                val firstScan = runCatching {
                    loadTracks(context, selectedUri) { batch ->
                        withContext(Dispatchers.Main.immediate) {
                            tracks = tracks + batch
                        }
                    }
                }.getOrDefault(emptyList())
                tracks = firstScan
                withContext(Dispatchers.IO) { saveTrackCache(context, selectedUri, firstScan) }
                scanning = false
            }
        }
    }

    LaunchedEffect(refreshRequest) {
        val selectedUri = folderUri
        if (refreshRequest > 0 && selectedUri != null) {
            scanning = true
            val existingSnapshot = tracks
            val updated = runCatching {
                loadTracks(context, selectedUri, existingSnapshot) { batch ->
                    withContext(Dispatchers.Main.immediate) {
                        tracks = (tracks + batch).distinctBy { it.id }
                    }
                }
            }.getOrDefault(existingSnapshot)
            tracks = updated
            withContext(Dispatchers.IO) { saveTrackCache(context, selectedUri, updated) }
            scanning = false
        }
    }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                currentId = player.currentMediaItem?.mediaId
                playing = player.isPlaying
                duration = player.duration.coerceAtLeast(0L)
                position = player.currentPosition.coerceAtLeast(0L)
            }
        }
        controller.addListener(listener)
        currentId = controller.currentMediaItem?.mediaId
        playing = controller.isPlaying
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (isActive) {
            if (controller != null) {
                currentId = controller.currentMediaItem?.mediaId
                playing = controller.isPlaying
                duration = controller.duration.takeIf { it > 0L } ?: 0L
                position = controller.currentPosition.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    val currentTrack = tracks.firstOrNull { it.id.toString() == currentId }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentTrack != null) {
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                }.orEmpty()
            }.getOrDefault("").ifBlank { uri.lastPathSegment.orEmpty() }
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val mime = context.contentResolver.getType(uri).orEmpty()
            val supported = extension in setOf("lrc", "txt") || mime.startsWith("text/")
            val imported = if (supported) readTextFile(context, uri) else null
            if (!imported.isNullOrBlank()) {
                val updatedTracks = tracks.map { if (it.id == currentTrack.id) it.copy(lyrics = imported) else it }
                tracks = updatedTracks
                folderUri?.let { selectedFolder ->
                    scope.launch(Dispatchers.IO) { saveTrackCache(context, selectedFolder, updatedTracks) }
                }
                lyricsImportError = null
                showLyrics = true
            } else {
                lyricsImportError = if (!supported) "Choose an .LRC or .TXT lyrics file." else "This file is empty or could not be read."
            }
        }
    }
    val pageBackground = if (darkMode) Color(0xFF11121A) else Color(0xFFF3F5FA)
    val panelColor = if (darkMode) Color(0xFF1D1F2B) else Color(0xFFFFFFFF)
    val panelAlt = if (darkMode) Color(0xFF292B39) else Color(0xFFE9EDF6)
    val accentColor = if (darkMode) Color(0xFFAFBCFF) else Color(0xFF5369D9)
    val accentSoft = if (darkMode) Color(0xFF303A58) else Color(0xFFDDE4FF)
    val mainText = if (darkMode) Color(0xFFF5F3FA) else Color(0xFF202638)
    val mutedText = if (darkMode) Color(0xFFB8B6C4) else Color(0xFF737D91)
    LaunchedEffect(currentTrack?.id, folderUri) {
        val selected = currentTrack
        val selectedFolder = folderUri
        if (selected != null && selectedFolder != null && selected.lyrics.isNullOrBlank()) {
            val found = withContext(Dispatchers.IO) {
                val trackDocument = findTrackDocument(context, selectedFolder, selected.uri)
                trackDocument?.let { readTrackLyrics(context, it) }
                    ?: findMatchingLyrics(context, selectedFolder, selected.uri, selected.title, selected.artist)
            }
            if (!found.isNullOrBlank() && currentId == selected.id.toString() && folderUri == selectedFolder) {
                val updatedTracks = tracks.map { if (it.id == selected.id) it.copy(lyrics = found) else it }
                tracks = updatedTracks
                withContext(Dispatchers.IO) { saveTrackCache(context, selectedFolder, updatedTracks) }
            }
        }
    }
    BackHandler(enabled = showSettingsMenu || showLyrics || showQueue || showCreatePlaylist || trackToAdd != null || showPlayer || selectedGroup != null || tab != "Songs") {
        when {
            showSettingsMenu -> showSettingsMenu = false
            showLyrics -> showLyrics = false
            showQueue -> showQueue = false
            showCreatePlaylist -> showCreatePlaylist = false
            trackToAdd != null -> trackToAdd = null
            showPlayer -> showPlayer = false
            selectedGroup != null -> selectedGroup = null
            tab != "Songs" -> { tab = "Songs"; search = "" }
        }
    }
    val filtered = when (tab) {
        "Favorites" -> tracks.filter { favorites.contains(it.id.toString()) }
        "Recent" -> recentIds.mapNotNull { id -> tracks.firstOrNull { it.id.toString() == id } }
        else -> tracks
    }.filter {
        search.isBlank() ||
            it.title.contains(search, true) ||
            it.artist.contains(search, true) ||
            it.album.contains(search, true)
    }

    fun playList(list: List<Track>, selected: Track) {
        if (controller == null || list.isEmpty()) return
        val items = list.map { it.toMediaItem() }
        val index = list.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        controller.setMediaItems(items, index, 0L)
        controller.prepare()
        controller.play()
        val recent = (listOf(selected.id.toString()) + recentIds).distinct().take(100)
        recentIds = recent
        prefs.edit().putString("recent_ids", recent.joinToString(",")).apply()
        showPlayer = true
    }

    fun toggleFavorite(track: Track) {
        val next = favorites.toMutableSet()
        if (!next.add(track.id.toString())) next.remove(track.id.toString())
        favorites = next
        prefs.edit().putStringSet("favorites", next).apply()
    }

    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme(
            primary = accentColor,
            onPrimary = Color(0xFF171421),
            background = pageBackground,
            surface = panelColor,
            surfaceVariant = panelAlt,
            onBackground = mainText,
            onSurface = mainText
        ) else lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            background = pageBackground,
            surface = panelColor,
            surfaceVariant = panelAlt,
            onBackground = mainText,
            onSurface = mainText
        )
    ) {
        if (splashVisible) {
            SplashScreen()
        } else {
        Scaffold(
            containerColor = pageBackground,
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(pageBackground)) {
                    if (currentTrack != null && !showPlayer) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(15.dp)).background(panelAlt).clickable { showPlayer = true }.padding(horizontal = 9.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AlbumArtwork(currentTrack.artworkUri, Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)), artworkData = currentTrack.artworkData)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(currentTrack.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    Text(currentTrack.artist, color = mutedText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { if (playing) controller?.pause() else controller?.play() }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play or pause") }
                                IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
                            }
                            LinearProgressIndicator(
                                progress = { if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)),
                                color = accentColor,
                                trackColor = accentSoft
                            )
                        }
                    }
                    NavigationBar(containerColor = pageBackground) {
                        listOf(
                            Triple("Songs", Icons.Filled.LibraryMusic, "Songs"),
                            Triple("Albums", Icons.Filled.Album, "Albums"),
                            Triple("Artists", Icons.Filled.Person, "Artists"),
                            Triple("Favorites", Icons.Filled.Favorite, "Favorites"),
                            Triple("Recent", Icons.Filled.Repeat, "Recent"),
                            Triple("Playlists", Icons.Filled.LibraryMusic, "Playlists")
                        ).forEach { item ->
                            NavigationBarItem(
                                selected = tab == item.first,
                                onClick = { tab = item.first; selectedGroup = null },
                                icon = { Icon(item.second, contentDescription = item.third) },
                                label = { Text(item.third) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accentColor,
                                    selectedTextColor = accentColor,
                                    indicatorColor = accentSoft,
                                    unselectedIconColor = mutedText,
                                    unselectedTextColor = mutedText
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).animateContentSize(animationSpec = tween(360, easing = FastOutSlowInEasing))
            ) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accentColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OFFLINE MUSIC", color = accentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Quick settings", tint = accentColor, modifier = Modifier.size(27.dp))
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                            modifier = Modifier.width(276.dp).background(panelColor, RoundedCornerShape(22.dp))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(accentSoft), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, tint = accentColor)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Quick settings", color = mainText, fontWeight = FontWeight.Bold)
                                    Text("Make it yours", color = mutedText, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            HorizontalDivider(color = mutedText.copy(alpha = 0.16f))
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Dark Mode", color = mainText, fontWeight = FontWeight.SemiBold)
                                        Text(if (darkMode) "On" else "Off", color = mutedText, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accentColor) },
                                trailingIcon = {
                                    Switch(checked = darkMode, onCheckedChange = {
                                        darkMode = it
                                        prefs.edit().putBoolean("dark_mode", it).apply()
                                    })
                                },
                                onClick = {
                                    darkMode = !darkMode
                                    prefs.edit().putBoolean("dark_mode", darkMode).apply()
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            )
                            HorizontalDivider(color = mutedText.copy(alpha = 0.16f))
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Change music folder", color = mainText, fontWeight = FontWeight.SemiBold)
                                        Text(folderUri?.let { DocumentFile.fromTreeUri(context, it)?.name } ?: "Choose your library folder", color = mutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = accentColor) },
                                onClick = {
                                    showSettingsMenu = false
                                    folderPicker.launch(folderUri)
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search songs, artists, albums") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = tab to selectedGroup,
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically(tween(300), initialOffsetY = { it / 14 })) togetherWith
                            (fadeOut(tween(180)) + slideOutVertically(tween(180), targetOffsetY = { -it / 18 }))
                    },
                    label = "libraryPageTransition"
                ) { page ->
                    val pageTab = page.first
                    val pageGroup = page.second
                    if (folderUri == null) {
                    Card(
                        Modifier.fillMaxWidth().padding(top = 34.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = panelColor)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(66.dp).clip(RoundedCornerShape(22.dp)).background(accentSoft), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = accentColor, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Your music, in one place", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = mainText)
                            Spacer(Modifier.height(6.dp))
                            Text("Choose the folder where your songs are stored.", color = mutedText, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = { folderPicker.launch(null) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Choose music folder")
                            }
                        }
                    }
                } else if (scanning && tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MusicLoadingIndicator(accentColor, accentSoft)
                            Spacer(Modifier.height(22.dp))
                            Text("Preparing your music", color = mainText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(5.dp))
                            Text("Finding your tracks and album details…", color = mutedText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accentColor, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No music found in this folder", fontWeight = FontWeight.Bold)
                            Text("Add your songs here, or choose a different folder in Settings.", color = mutedText)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { refreshRequest++ }) { Text("Refresh folder") }
                            TextButton(onClick = { folderPicker.launch(folderUri) }) { Text("Change music folder") }
                        }
                    }
                } else if (pageTab == "Playlists") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { playlistName = ""; showCreatePlaylist = true }) { Text("+ New") }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(playlistNames.toList().sorted()) { name ->
                            val ids = prefs.getStringSet("playlist_$name", emptySet()) ?: emptySet()
                            val playlistTracks = tracks.filter { ids.contains(it.id.toString()) }
                            Card(Modifier.fillMaxWidth().clickable { playlistTracks.firstOrNull()?.let { playList(playlistTracks, it) } }, colors = CardDefaults.cardColors(containerColor = panelColor)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LibraryMusic, tint = accentColor, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text("${playlistTracks.size} tracks", color = mutedText) }
                                    TextButton(onClick = { tracks.firstOrNull()?.let { t -> prefs.edit().putStringSet("playlist_$name", ids + t.id.toString()).apply() } }) { Text("Add first song") }
                                }
                            }
                        }
                    }
                } else if ((pageTab == "Albums" || pageTab == "Artists") && pageGroup != null) {
                    val group = pageGroup!!
                    val groupTracks = if (pageTab == "Albums") tracks.filter { it.album == group } else tracks.filter { it.artist == group }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedGroup = null }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(group, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (pageTab == "Albums") "Album" else "Artist", color = mutedText, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${groupTracks.size} songs", color = mutedText, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(panelColor).padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pageTab == "Artists") {
                                    ArtistArtwork(group, artistImagesVersion, Modifier.size(76.dp))
                                } else {
                                    val albumCover = groupTracks.firstOrNull()
                                    AlbumArtwork(albumCover?.artworkUri, Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)), artworkData = albumCover?.artworkData)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${groupTracks.size} songs in your library", color = mutedText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Songs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { groupTracks.firstOrNull()?.let { playList(groupTracks, it) } }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Text("Play all")
                                }
                            }
                        }
                        items(groupTracks, key = { it.id }) { track ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                    .background(if (currentId == track.id.toString()) accentSoft else panelColor)
                                    .clickable { playList(groupTracks, track) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AlbumArtwork(track.artworkUri, Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)), artworkData = track.artworkData)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(if (pageTab == "Albums") track.artist else track.album, maxLines = 1, overflow = TextOverflow.Ellipsis, color = mutedText, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(formatTime(track.duration), color = mutedText, style = MaterialTheme.typography.labelSmall)
                                IconButton(onClick = { toggleFavorite(track) }) {
                                    Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = if (favorites.contains(track.id.toString())) accentColor else mutedText)
                                }
                            }
                        }
                    }
                } else if (pageTab == "Albums" || pageTab == "Artists") {
                    val groups = if (pageTab == "Albums") tracks.map { it.album }.distinct().sorted() else tracks.map { it.artist }.distinct().sorted()
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (pageTab == "Artists") item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Artists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                IconButton(enabled = !artistFetchState, onClick = {
                                    scope.launch {
                                        artistFetchState = true
                                        val names = tracks.map { it.artist.trim() }.filter { it.isNotBlank() && !it.equals("Unknown artist", true) }.distinct()
                                        withContext(Dispatchers.IO) {
                                            names.filterNot { artistArtworkFile(context, it).exists() }.chunked(5).forEach { batch ->
                                                kotlinx.coroutines.coroutineScope {
                                                    batch.map { name -> async(Dispatchers.IO) { fetchArtistArtwork(context, name) } }.forEach { it.await() }
                                                }
                                            }
                                        }
                                        artistImagesVersion++
                                        artistFetchState = false
                                    }
                                }) { if (artistFetchState) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Add, contentDescription = "Download artist photos", tint = accentColor) }
                            }
                        } else item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { Text("Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        gridItems(groups) { group ->
                            val groupTracks = if (pageTab == "Albums") tracks.filter { it.album == group } else tracks.filter { it.artist == group }
                            Column(Modifier.fillMaxWidth().clickable { selectedGroup = group }, horizontalAlignment = Alignment.CenterHorizontally) {
                                if (pageTab == "Artists") ArtistArtwork(group, artistImagesVersion, Modifier.size(96.dp).clip(CircleShape))
                                else { val cover = groupTracks.firstOrNull(); AlbumArtwork(cover?.artworkUri, Modifier.size(96.dp).clip(CircleShape), artworkData = cover?.artworkData) }
                                Spacer(Modifier.height(7.dp))
                                Text(group, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text("${groupTracks.size} tracks", color = mutedText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pageTab, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(filtered.size.toString() + " tracks", color = mutedText, style = MaterialTheme.typography.labelMedium)
                    }
                    if (scanning) {
                        Text("Scanning… ${tracks.size} songs found", color = accentColor, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        state = songsListState,
                        modifier = Modifier.pointerInput(refreshRequest, scanning) {
                            var dragDistance = 0f
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    if (songsListState.firstVisibleItemIndex == 0 && amount > 0f) dragDistance += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (dragDistance > 90f && !scanning) refreshRequest++
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f }
                            )
                        },
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filtered, key = { it.id }) { track ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240))
                            ) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (currentId == track.id.toString()) accentSoft else panelColor)
                                    .clickable { playList(filtered, track) }
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AlbumArtwork(track.artworkUri, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), artworkData = track.artworkData)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(track.artist + " • " + track.album, maxLines = 1, overflow = TextOverflow.Ellipsis, color = mutedText, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { toggleFavorite(track) }) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = "Favorite",
                                        tint = if (favorites.contains(track.id.toString())) accentColor else mutedText
                                    )
                                }
                                Box {
                                    IconButton(onClick = { songMenuTrack = track; songMenuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Song options", tint = mutedText)
                                    }
                                    DropdownMenu(
                                        expanded = songMenuExpanded && songMenuTrack?.id == track.id,
                                        onDismissRequest = { songMenuExpanded = false; songMenuTrack = null },
                                        modifier = Modifier.background(panelColor, RoundedCornerShape(18.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Play now", color = mainText) },
                                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = accentColor) },
                                            onClick = { playList(filtered, track); songMenuExpanded = false; songMenuTrack = null }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to playlist", color = mainText) },
                                            leadingIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = accentColor) },
                                            onClick = { trackToAdd = track; songMenuExpanded = false; songMenuTrack = null }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (favorites.contains(track.id.toString())) "Remove from favorites" else "Add to favorites", color = mainText) },
                                            leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = accentColor) },
                                            onClick = { toggleFavorite(track); songMenuExpanded = false; songMenuTrack = null }
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
                }
            }
        }


        if (trackToAdd != null) {
            AlertDialog(
                onDismissRequest = { trackToAdd = null },
                title = { Text("Add to playlist") },
                text = {
                    if (playlistNames.isEmpty()) Text("Create a playlist first.")
                    else Column {
                        playlistNames.toList().sorted().forEach { name ->
                            TextButton(onClick = {
                                val key = "playlist_$name"
                                val ids = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
                                prefs.edit().putStringSet(key, ids + trackToAdd!!.id.toString()).apply()
                                playlistRefresh++
                                trackToAdd = null
                            }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { trackToAdd = null }) { Text("Close") } }
            )
        }
        if (showCreatePlaylist) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylist = false },
                title = { Text("New playlist") },
                text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Playlist name") }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        val name = playlistName.trim()
                        if (name.isNotEmpty()) {
                            playlistNames = playlistNames + name
                            prefs.edit().putStringSet("playlist_names", playlistNames).putStringSet("playlist_$name", emptySet()).apply()
                        }
                        showCreatePlaylist = false
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") } }
            )
        }
        if (showQueue && controller != null) {
            AlertDialog(
                onDismissRequest = { showQueue = false },
                title = { Text("Play queue") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items((0 until controller.mediaItemCount).toList()) { index ->
                            val item = controller.getMediaItemAt(index)
                            Row(Modifier.fillMaxWidth().clickable { controller.seekTo(index, 0L); controller.play(); showQueue = false }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.mediaMetadata.title?.toString() ?: "Unknown track", modifier = Modifier.weight(1f), maxLines = 1)
                                if (index == controller.currentMediaItemIndex) Text("Playing", color = accentColor)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Close") } }
            )
        }

        if (showLyrics && currentTrack != null) {
            val lines = parseLyricsLines(currentTrack.lyrics.orEmpty())
            val timed = lines.any { it.first != null }
            val active = if (timed) lines.indexOfLast { it.first != null && it.first!! <= position }.coerceAtLeast(0) else if (lines.isNotEmpty() && duration > 0) ((position.toFloat()/duration)*lines.size).toInt().coerceIn(0,lines.lastIndex) else 0
            val from = lines.getOrNull(active)?.first ?: 0L
            val until = lines.drop(active+1).firstOrNull { it.first != null }?.first ?: duration.coerceAtLeast(from+1)
            val fill by androidx.compose.animation.core.animateFloatAsState(if (timed) ((position-from).toFloat()/(until-from).coerceAtLeast(1)).coerceIn(0f,1f) else if (lines.isNotEmpty()) ((position.toFloat()/duration.coerceAtLeast(1))*lines.size-active).coerceIn(0f,1f) else 0f, tween(180), label="lyricFill")
            Dialog(onDismissRequest = { showLyrics = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxSize(), color = pageBackground) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showLyrics = false }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                            Text("LYRICS  •  " + currentTrack.title, color = mainText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (lines.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accentColor, modifier = Modifier.size(46.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("No lyrics found", color = mainText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Add an LRC or TXT file from your phone.", color = mutedText)
                                    lyricsImportError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                    Spacer(Modifier.height(18.dp))
                                    Button(onClick = { lyricsPicker.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(16.dp)) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add local lyrics")
                                    }
                                }
                            }
                        }
                        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 28.dp)) {
                            items(lines.size) { i ->
                                val text = lines[i].second
                                if (i == active) Box {
                                    Text(text, color = mutedText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(text, color = mainText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.drawWithContent { clipRect(right = size.width * fill) { this@drawWithContent.drawContent() } })
                                } else Text(text, color = if (i < active) mutedText else mutedText, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }

        if (showPlayer && currentTrack != null) {
            Dialog(
                onDismissRequest = { showPlayer = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Surface(Modifier.fillMaxSize(), color = pageBackground) {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showPlayer = false }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Close player", tint = Color.White) }
                            Spacer(Modifier.weight(1f))
                            Text("NOW PLAYING", color = accentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { showPlayerMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White) }
                                DropdownMenu(expanded = showPlayerMenu, onDismissRequest = { showPlayerMenu = false }) {
                                    DropdownMenuItem(text = { Text("Shuffle") }, onClick = { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle; showPlayerMenu = false })
                                    DropdownMenuItem(text = { Text("Repeat") }, onClick = { repeat = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; controller?.repeatMode = repeat; showPlayerMenu = false })
                                    DropdownMenuItem(text = { Text("Play queue") }, onClick = { showQueue = true; showPlayerMenu = false })
                                }
                            }
                        }
                        Spacer(Modifier.weight(0.55f))
                        AlbumArtwork(currentTrack.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp)), large = true, artworkData = currentTrack.artworkData)
                        Spacer(Modifier.height(28.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(currentTrack.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(currentTrack.artist, color = mutedText, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { toggleFavorite(currentTrack) }) {
                                Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = if (favorites.contains(currentTrack.id.toString())) accentColor else mutedText)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Slider(
                            value = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { if (duration > 0L) position = (it * duration).toLong() },
                            onValueChangeFinished = { controller?.seekTo(position) }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(position), color = mutedText, style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(duration), color = mutedText, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            FilledTonalButton(
                                onClick = { showLyrics = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = accentSoft, contentColor = accentColor)
                            ) {
                                Icon(Icons.Filled.MusicNote, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("Lyrics")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }) {
                                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) accentColor else mutedText)
                            }
                            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(38.dp)) }
                            IconButton(onClick = { if (playing) controller?.pause() else controller?.play() }, modifier = Modifier.size(68.dp).background(accentColor, CircleShape)) {
                                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play pause", tint = Color.Black, modifier = Modifier.size(40.dp))
                            }
                            IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(38.dp)) }
                            IconButton(onClick = {
                                repeat = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                                controller?.repeatMode = repeat
                            }) { Icon(Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (repeat == Player.REPEAT_MODE_OFF) mutedText else accentColor) }
                        }
                        Spacer(Modifier.weight(0.65f))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MusicLoadingIndicator(accent: Color, softAccent: Color) {
    val transition = rememberInfiniteTransition(label = "musicLoading")
    val heights = (0 until 5).map { index ->
        transition.animateFloat(
            initialValue = 13f,
            targetValue = 38f,
            animationSpec = infiniteRepeatable(
                animation = tween(560, delayMillis = index * 95, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "musicBar$index"
        )
    }
    Box(
        Modifier.size(width = 142.dp, height = 94.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(softAccent.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            heights.forEachIndexed { index, height ->
                Box(
                    Modifier.width(8.dp).height(height.value.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (index == 2) accent else accent.copy(alpha = 0.72f))
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val drop = remember { Animatable(-170f) }
    val spin = remember { Animatable(0f) }
    var landed by remember { mutableStateOf(false) }
    val expansion by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (landed) 1f else 0f,
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "logoExpansion"
    )
    LaunchedEffect(Unit) {
        launch { spin.animateTo(720f, animationSpec = tween(1120, easing = FastOutSlowInEasing)) }
        drop.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 1120
                -170f at 0
                20f at 650
                -13f at 820
                0f at 1010 using FastOutSlowInEasing
            }
        )
        landed = true
    }
    val iconSize = androidx.compose.ui.unit.lerp(62.dp, 118.dp, expansion)
    val corner = (50f - expansion * 20f).coerceIn(28f, 50f)
    Box(Modifier.fillMaxSize().background(Color(0xFF100D0D)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(iconSize).offset(y = drop.value.dp)
                    .graphicsLayer { rotationZ = spin.value; scaleX = 0.78f + expansion * 0.22f; scaleY = 0.78f + expansion * 0.22f }
                    .clip(RoundedCornerShape(corner.dp)).background(Color(0xFFE5393F)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(76.dp * expansion.coerceAtLeast(0.25f)).clip(CircleShape).background(Color(0xFFB91F2A)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp * expansion.coerceAtLeast(0.35f)))
                }
            }
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(
                visible = landed,
                enter = fadeIn(tween(650)) + slideInVertically(animationSpec = tween(650, easing = FastOutSlowInEasing), initialOffsetY = { it / 2 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OFFLINE MUSIC", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(7.dp))
                    Text("YOUR MUSIC. YOUR WORLD.", color = Color(0xFFFF777B), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

 
@Composable
private fun AlbumArtwork(uri: Uri?, modifier: Modifier = Modifier, large: Boolean = false, artworkData: ByteArray? = null) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, uri, artworkData) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    artworkData != null -> BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)?.asImageBitmap()
                    uri != null -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    else -> null
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (large) 92.dp else 24.dp))
        }
    }
}