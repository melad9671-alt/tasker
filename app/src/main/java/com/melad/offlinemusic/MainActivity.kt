package com.melad.offlinemusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
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
    val artworkData: ByteArray? = null
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

private fun loadTracks(context: Context, treeUri: Uri): List<Track> {
    val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return emptyList()
    val files = linkedMapOf<String, DocumentFile>()
    val pending = ArrayDeque<DocumentFile>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val folder = pending.removeFirst()
        val children = runCatching { folder.listFiles().toList() }.getOrDefault(emptyList())
        children.forEach { file ->
            if (file.isDirectory) pending.add(file)
            else {
                val name = file.name.orEmpty().lowercase()
                val extensions = listOf(".mp3", ".m4a", ".m4b", ".aac", ".flac", ".ogg", ".opus", ".wav", ".wma", ".aiff", ".aif", ".3gp", ".amr")
                val isAudio = extensions.any { name.endsWith(it) } || file.type?.startsWith("audio/", ignoreCase = true) == true
                if (isAudio) files[file.uri.toString()] = file
            }
        }
    }
    return files.values.map { file ->
        val fallbackTitle = file.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Unknown title"
        val retriever = MediaMetadataRetriever()
        val opened = runCatching { retriever.setDataSource(context, file.uri); true }.getOrDefault(false)
        fun metadata(key: Int): String? = if (opened) runCatching { retriever.extractMetadata(key)?.takeIf { it.isNotBlank() } }.getOrNull() else null
        val title = metadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallbackTitle
        val artist = metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown artist"
        val album = metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown album"
        val duration = metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        runCatching { retriever.release() }
        Track(file.uri.toString().hashCode().toLong(), title, artist, album, duration, file.uri, null, if (opened) runCatching { retriever.embeddedPicture }.getOrNull() else null)
    }.sortedBy { it.title.lowercase() }
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
                .setArtworkData(artworkData, "image/*")
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
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var trackToAdd by remember { mutableStateOf<Track?>(null) }
    var playlistRefresh by remember { mutableIntStateOf(0) }
    var playlistName by remember { mutableStateOf("") }
    var playlistNames by remember { mutableStateOf(prefs.getStringSet("playlist_names", emptySet())?.toSet() ?: emptySet()) }
    var recentIds by remember { mutableStateOf(prefs.getString("recent_ids", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }
    var splashVisible by remember { mutableStateOf(true) }

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

    LaunchedEffect(folderUri, refreshRequest) {
        val selectedUri = folderUri
        if (selectedUri == null) {
            tracks = emptyList()
            scanning = false
        } else {
            scanning = true
            tracks = withContext(Dispatchers.IO) {
                runCatching { loadTracks(context, selectedUri) }.getOrDefault(emptyList())
            }
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
        if (splashVisible) {
            SplashScreen()
        } else {
        Scaffold(
            containerColor = Color(0xFF090D0B),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF111713)) {
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
                            label = { Text(item.third) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).animateContentSize(animationSpec = tween(360, easing = FastOutSlowInEasing))
            ) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1ED760)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OFFLINE MUSIC", color = Color(0xFF1ED760), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { tab = "Settings"; selectedGroup = null }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0xFF1ED760), modifier = Modifier.size(27.dp))
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
                    if (pageTab == "Settings") {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B18)), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Music folder", fontWeight = FontWeight.Bold)
                                    Text("Only scan songs inside this folder", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                if (folderUri == null) "No folder selected yet" else "A music folder is selected. The app scans audio files inside it and its subfolders only.",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { folderPicker.launch(folderUri) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (folderUri == null) "Choose music folder" else "Change music folder")
                            }
                            if (folderUri != null) {
                                TextButton(onClick = {
                                    folderUri = null
                                    prefs.edit().remove("music_folder_uri").apply()
                                    tracks = emptyList()
                                }, modifier = Modifier.fillMaxWidth()) { Text("Clear selected folder") }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Your audio files stay on your device. The app does not upload them.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                } else if (folderUri == null) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Choose your music folder", fontWeight = FontWeight.Bold)
                        Text("Offline Music will read songs only from the folder you select.", color = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { folderPicker.launch(null) }) { Text("Choose folder") }
                    }
                } else if (scanning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF1ED760))
                            Spacer(Modifier.height(12.dp))
                            Text("Scanning your music folder…", color = Color.LightGray)
                        }
                    }
                } else if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No music found in this folder", fontWeight = FontWeight.Bold)
                            Text("Add your songs here, or choose a different folder in Settings.", color = Color.LightGray)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { refreshRequest++ }) { Text("Refresh folder") }
                            TextButton(onClick = { tab = "Settings" }) { Text("Change music folder") }
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
                            Card(Modifier.fillMaxWidth().clickable { playlistTracks.firstOrNull()?.let { playList(playlistTracks, it) } }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151B18))) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LibraryMusic, tint = Color(0xFF1ED760), contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text("${playlistTracks.size} tracks", color = Color.LightGray) }
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
                                    Text(if (pageTab == "Albums") "Album" else "Artist", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${groupTracks.size} songs", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF251817)).padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF3A2424)), contentAlignment = Alignment.Center) {
                                    Icon(if (pageTab == "Albums") Icons.Filled.Album else Icons.Filled.Person, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(38.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${groupTracks.size} songs in your library", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
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
                                    .background(if (currentId == track.id.toString()) Color(0xFF2A2020) else Color(0xFF151B18))
                                    .clickable { playList(groupTracks, track) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AlbumArtwork(track.artworkUri, Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(if (pageTab == "Albums") track.artist else track.album, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(formatTime(track.duration), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                IconButton(onClick = { toggleFavorite(track) }) {
                                    Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = if (favorites.contains(track.id.toString())) Color(0xFFFF5A5F) else Color.Gray)
                                }
                            }
                        }
                    }
                } else if (pageTab == "Albums" || pageTab == "Artists") {
                    val groups = if (pageTab == "Albums") tracks.map { it.album }.distinct().sorted()
                    else tracks.map { it.artist }.distinct().sorted()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups) { group ->
                            val groupTracks = if (pageTab == "Albums") tracks.filter { it.album == group } else tracks.filter { it.artist == group }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedGroup = group },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B18)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF352222)), contentAlignment = Alignment.Center) {
                                        Icon(if (pageTab == "Albums") Icons.Filled.Album else Icons.Filled.Person, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(27.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(group, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(groupTracks.size.toString() + " tracks", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Open", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pageTab, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                AlbumArtwork(track.artworkUri, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(track.artist + " • " + track.album, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { trackToAdd = track }) {
                                    Icon(Icons.Filled.LibraryMusic, contentDescription = "Add to playlist", tint = Color.LightGray)
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
                                if (index == controller.currentMediaItemIndex) Text("Playing", color = Color(0xFF1ED760))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Close") } }
            )
        }
        if (showPlayer && currentTrack != null) {
            AlertDialog(
                onDismissRequest = { showPlayer = false },
                containerColor = Color(0xFF151B18),
                title = { Text("NOW PLAYING", color = Color(0xFF1ED760), style = MaterialTheme.typography.labelLarge) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        AlbumArtwork(currentTrack.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)), large = true)
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
                        TextButton(onClick = { showQueue = true }) { Text("View queue") }
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
private fun AlbumArtwork(uri: Uri?, modifier: Modifier = Modifier, large: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    Box(modifier.background(Color(0xFF26372B)), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF1ED760), modifier = Modifier.size(if (large) 92.dp else 24.dp))
        }
    }
}
