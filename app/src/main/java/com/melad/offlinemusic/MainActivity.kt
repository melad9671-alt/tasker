package com.melad.offlinemusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri
)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

private fun loadTracks(context: Context): List<Track> {
    val result = mutableListOf<Track>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            result += Track(
                id = id,
                title = cursor.getString(titleCol) ?: "Unknown title",
                artist = cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                album = cursor.getString(albumCol)?.takeUnless { it == "<unknown>" } ?: "Unknown album",
                duration = cursor.getLong(durationCol),
                uri = Uri.withAppendedPath(collection, id.toString())
            )
        }
    }
    return result
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
                .build()
        )
        .build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineMusicApp(controller: MediaController?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("offline_music", Context.MODE_PRIVATE) }
    var permissionGranted by remember { mutableStateOf(hasAudioPermission(context)) }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var tab by remember { mutableStateOf("Songs") }
    var search by remember { mutableStateOf("") }
    var currentId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showPlayer by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(audioPermission())
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            tracks = withContext(Dispatchers.IO) { runCatching { loadTracks(context) }.getOrDefault(emptyList()) }
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
    val filtered = when (tab) {
        "Favorites" -> tracks.filter { favorites.contains(it.id.toString()) }
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
        showPlayer = true
    }

    fun toggleFavorite(track: Track) {
        val next = favorites.toMutableSet()
        if (!next.add(track.id.toString())) next.remove(track.id.toString())
        favorites = next
        prefs.edit().putStringSet("favorites", next).apply()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1ED760),
            onPrimary = Color(0xFF07130B),
            background = Color(0xFF090D0B),
            surface = Color(0xFF151B18),
            surfaceVariant = Color(0xFF202923),
            onBackground = Color(0xFFF4F7F4),
            onSurface = Color(0xFFF4F7F4)
        )
    ) {
        Scaffold(
            containerColor = Color(0xFF090D0B),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF111713)) {
                    listOf(
                        Triple("Songs", Icons.Filled.LibraryMusic, "Songs"),
                        Triple("Albums", Icons.Filled.Album, "Albums"),
                        Triple("Artists", Icons.Filled.Person, "Artists"),
                        Triple("Favorites", Icons.Filled.Favorite, "Favorites")
                    ).forEach { item ->
                        NavigationBarItem(
                            selected = tab == item.first,
                            onClick = { tab = item.first },
                            icon = { Icon(item.second, contentDescription = item.third) },
                            label = { Text(item.third) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1ED760)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("OFFLINE MUSIC", color = Color(0xFF1ED760), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                if (!permissionGranted) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF1ED760), modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Let Offline Music find your audio", fontWeight = FontWeight.Bold)
                        Text("Your files stay on this device. Nothing is uploaded.", color = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(audioPermission()) }) { Text("Allow audio access") }
                    }
                } else if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF1ED760), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No music found", fontWeight = FontWeight.Bold)
                            Text("Add audio files to your device and refresh the library.", color = Color.LightGray)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { tracks = loadTracks(context) }) { Text("Refresh library") }
                        }
                    }
                } else if (tab == "Albums" || tab == "Artists") {
                    val groups = if (tab == "Albums") tracks.map { it.album }.distinct().sorted()
                    else tracks.map { it.artist }.distinct().sorted()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups) { group ->
                            val groupTracks = if (tab == "Albums") tracks.filter { it.album == group } else tracks.filter { it.artist == group }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    groupTracks.firstOrNull()?.let { playList(groupTracks, it) }
                                },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B18)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF26372B)), contentAlignment = Alignment.Center) {
                                        Icon(if (tab == "Albums") Icons.Filled.Album else Icons.Filled.Person, contentDescription = null, tint = Color(0xFF1ED760))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(group, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(groupTracks.size.toString() + " tracks", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color(0xFF1ED760))
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tab, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(filtered.size.toString() + " tracks", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(filtered, key = { it.id }) { track ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (currentId == track.id.toString()) Color(0xFF1A2A20) else Color(0xFF121815))
                                    .clickable { playList(filtered, track) }
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF26372B)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF1ED760))
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(track.artist + " • " + track.album, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { toggleFavorite(track) }) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = "Favorite",
                                        tint = if (favorites.contains(track.id.toString())) Color(0xFF1ED760) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
                if (currentTrack != null && !showPlayer) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF26372B))
                            .clickable { showPlayer = true }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF1ED760))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(currentTrack.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(currentTrack.artist, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { if (playing) controller?.pause() else controller?.play() }) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play or pause")
                        }
                        IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (showPlayer && currentTrack != null) {
            AlertDialog(
                onDismissRequest = { showPlayer = false },
                containerColor = Color(0xFF151B18),
                title = { Text("NOW PLAYING", color = Color(0xFF1ED760), style = MaterialTheme.typography.labelLarge) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF26372B)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF1ED760), modifier = Modifier.size(92.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(currentTrack.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentTrack.artist, color = Color.LightGray)
                        Spacer(Modifier.height(12.dp))
                        Slider(
                            value = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { if (duration > 0L) position = (it * duration).toLong() },
                            onValueChangeFinished = { controller?.seekTo(position) }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(position), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(duration), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }) {
                                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) Color(0xFF1ED760) else Color.LightGray)
                            }
                            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp)) }
                            IconButton(onClick = { if (playing) controller?.pause() else controller?.play() }, modifier = Modifier.size(58.dp).background(Color(0xFF1ED760), CircleShape)) {
                                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play pause", tint = Color.Black, modifier = Modifier.size(34.dp))
                            }
                            IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp)) }
                            IconButton(onClick = {
                                repeat = when (repeat) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                controller?.repeatMode = repeat
                            }) {
                                Icon(Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (repeat == Player.REPEAT_MODE_OFF) Color.LightGray else Color(0xFF1ED760))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { toggleFavorite(currentTrack) }) {
                        Text(if (favorites.contains(currentTrack.id.toString())) "Remove favorite" else "Add favorite")
                    }
                },
                dismissButton = { TextButton(onClick = { showPlayer = false }) { Text("Close") } }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
