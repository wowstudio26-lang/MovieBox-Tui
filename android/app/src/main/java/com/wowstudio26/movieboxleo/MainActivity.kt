@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wowstudio26.movieboxleo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MovieBoxLeoTheme { MovieBoxLeoApp() } }
    }
}

@Composable
private fun MovieBoxLeoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF09090B),
            surface = Color(0xFF111114),
            surfaceVariant = Color(0xFF1A1A20),
            primary = Color(0xFFE7B45B),
            secondary = Color(0xFFB8A7D9)
        ),
        content = content
    )
}

@Composable
private fun MovieBoxLeoApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedResult by remember { mutableStateOf<SearchResult?>(null) }

    Surface(Modifier.fillMaxSize()) {
        if (selectedResult != null) {
            DetailsScreen(selectedResult!!, onBack = { selectedResult = null })
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> HomeScreen(query, { query = it }) { selectedTab = 1 }
                        1 -> SearchScreen(query, { query = it }) { selectedResult = it }
                        2 -> LibraryScreen()
                        else -> SettingsScreen()
                    }
                }
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Library") })
                    NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("MovieBox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("LEO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
            singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search movies, series, anime…") }
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSearch, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Search")
        }
        Spacer(Modifier.height(24.dp))
        Text("Search the MovieBox provider through the native Rust engine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchScreen(query: String, onQueryChange: (String) -> Unit, onOpen: (SearchResult) -> Unit) {
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun performSearch() {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty() || searching) return
        searching = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { RustBridge.search(cleanQuery) }
            result.onSuccess { results = it }.onFailure { results = emptyList(); error = it.message }
            searching = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
            singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search…") }
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { performSearch() }, enabled = query.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth()) {
            Text(if (searching) "Searching…" else "Search")
        }
        Spacer(Modifier.height(18.dp))

        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            searching -> Text("Searching MovieBox provider…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            results.isEmpty() && query.isNotBlank() -> Text("No results found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            results.isEmpty() -> Text("Enter a title and search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> results.forEach { result ->
                SearchResultCard(result) { onOpen(result) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(92.dp).width(76.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF252530), Color(0xFF15151A)))
                ),
                contentAlignment = Alignment.Center
            ) { Text("POSTER", style = MaterialTheme.typography.labelSmall) }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(result.title, fontWeight = FontWeight.SemiBold)
                if (result.year.isNotBlank()) Text(result.year, style = MaterialTheme.typography.bodySmall)
                Text(result.mediaType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DetailsScreen(result: SearchResult, onBack: () -> Unit) {
    var details by remember { mutableStateOf<MediaDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playbackInfo by remember { mutableStateOf<PlaybackInfo?>(null) }
    var selectedLanguageId by remember(result.id) { mutableStateOf(result.id) }
    var selectedResolution by remember(result.id) { mutableIntStateOf(0) }
    var selectedSeason by remember(result.id) { mutableIntStateOf(0) }
    var selectedEpisode by remember(result.id) { mutableIntStateOf(0) }
    var seasonMenuOpen by remember { mutableStateOf(false) }
    var episodeMenuOpen by remember { mutableStateOf(false) }
    var downloadBusy by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    var downloadSelectionInfo by remember { mutableStateOf<PlaybackInfo?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler { onBack() }

    LaunchedEffect(result.id) {
        val loaded = withContext(Dispatchers.IO) { RustBridge.details(result.id) }
        loaded.onSuccess { details = it }.onFailure { error = it.message ?: "Unable to load details" }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        when {
            loading -> Text("Loading details…", Modifier.padding(18.dp))
            error != null -> Text(error!!, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error)
            details == null -> Text("Details unavailable.", Modifier.padding(18.dp))
            else -> {
                val item = details!!
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(300.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF252530), Color(0xFF15151A)))
                        ),
                        contentAlignment = Alignment.Center
                    ) { Text("POSTER", style = MaterialTheme.typography.titleMedium) }

                    Spacer(Modifier.height(18.dp))
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val meta = listOf(item.year, item.mediaType.replaceFirstChar { it.uppercase() }, item.duration).filter { it.isNotBlank() }
                    if (meta.isNotEmpty()) Text(meta.joinToString("  •  "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.genres.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(item.genres.joinToString(" • "), color = MaterialTheme.colorScheme.primary)
                    }
                    if (item.imdbRating.isNotBlank()) Text("IMDb " + item.imdbRating, Modifier.padding(top = 8.dp))
                    if (item.tagline.isNotBlank()) Text(item.tagline, Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
                    if (item.description.isNotBlank()) Text(item.description, Modifier.padding(top = 12.dp))
                    if (item.director.isNotBlank()) Text("Director: " + item.director, Modifier.padding(top = 12.dp))
                    if (item.stars.isNotBlank()) Text("Cast: " + item.stars, Modifier.padding(top = 6.dp))

                    LaunchedEffect(item.dubs) {
                        if (item.dubs.isNotEmpty() && item.dubs.none { it.subjectId == selectedLanguageId }) selectedLanguageId = item.dubs.first().subjectId
                    }
                    if (item.dubs.size > 1) {
                        Spacer(Modifier.height(16.dp))
                        Text("Language / Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        var languageMenuOpen by remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { languageMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                val selected = item.dubs.firstOrNull { it.subjectId == selectedLanguageId }
                                Text(selected?.label ?: selected?.language ?: "Original")
                            }
                            DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                                item.dubs.forEach { dub ->
                                    DropdownMenuItem(
                                        text = { Text(dub.label.ifBlank { dub.language.ifBlank { "Original" } }) },
                                        onClick = { selectedLanguageId = dub.subjectId; selectedResolution = 0; playbackInfo = null; languageMenuOpen = false }
                                    )
                                }
                            }
                        }
                    }
                    if (item.seasons.isNotEmpty()) {
                        val seasonInfo = item.seasons.find { it.number == selectedSeason }
                            ?: item.seasons.first()
                        LaunchedEffect(item.id, item.seasons) {
                            selectedSeason = item.seasons.first().number
                            selectedEpisode = item.seasons.first().episodes.firstOrNull()?.number ?: 1
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { seasonMenuOpen = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Season " + selectedSeason)
                                }
                                DropdownMenu(
                                    expanded = seasonMenuOpen,
                                    onDismissRequest = { seasonMenuOpen = false }
                                ) {
                                    item.seasons.forEach { season ->
                                        DropdownMenuItem(
                                            text = { Text("Season " + season.number) },
                                            onClick = {
                                                selectedSeason = season.number
                                                selectedEpisode = season.episodes.firstOrNull()?.number ?: 1
                                                seasonMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box(Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { episodeMenuOpen = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Episode " + selectedEpisode)
                                }
                                DropdownMenu(
                                    expanded = episodeMenuOpen,
                                    onDismissRequest = { episodeMenuOpen = false }
                                ) {
                                    seasonInfo.episodes.forEach { episode ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "EP " + episode.number +
                                                        if (episode.title.isNotBlank()) " • " + episode.title else ""
                                                )
                                            },
                                            onClick = {
                                                selectedEpisode = episode.number
                                                episodeMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        seasonInfo.episodes.find { it.number == selectedEpisode }?.title?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    if (playbackInfo == null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (playing || downloadBusy) return@Button
                                    playing = true
                                    error = null
                                    scope.launch {
                                        val playback = withContext(Dispatchers.IO) { RustBridge.playback(selectedLanguageId, selectedSeason, selectedEpisode) }
                                        playback.onSuccess { info -> playbackInfo = info; selectedResolution = info.defaultOption.resolution }
                                            .onFailure { error = it.message ?: "Unable to resolve stream" }
                                        playing = false
                                    }
                                },
                                enabled = !downloadBusy, modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (playing) "Resolving…" else "Play")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (playing || downloadBusy) return@OutlinedButton
                                    downloadBusy = true
                                    error = null
                                    downloadMessage = null
                                    scope.launch {
                                        val playback = withContext(Dispatchers.IO) { RustBridge.playback(selectedLanguageId, selectedSeason, selectedEpisode) }
                                        playback.onSuccess { info ->
                                            downloadSelectionInfo = info
                                        }.onFailure { error = it.message ?: "Unable to prepare download" }
                                        downloadBusy = false
                                    }
                                },
                                enabled = !playing, modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (downloadBusy) "Preparing…" else "Download")
                            }
                        }
                        downloadMessage?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
                    } else {
                        PlaybackPlayer(playbackInfo!!, selectedResolution, { selectedResolution = it }) { playbackInfo = null }
                    }
                    downloadSelectionInfo?.let { info ->
                        DownloadSelectionDialog(
                            title = item.title,
                            languages = item.dubs,
                            selectedLanguageId = selectedLanguageId,
                            options = info.options,
                            initialResolution = info.defaultOption.resolution,
                            onLanguageChange = { selectedLanguageId = it },
                            onConfirm = { resolution ->
                                val option = info.options.firstOrNull { it.resolution == resolution } ?: info.defaultOption
                                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    (context as? MainActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
                                }
                                val downloadId = "moviebox:" + selectedLanguageId + ":s" + selectedSeason + "e" + selectedEpisode + ":r" + option.resolution
                                val title = if (item.seasons.isNotEmpty()) {
                                    item.title + " • S" + selectedSeason.toString().padStart(2, '0') + "E" + selectedEpisode.toString().padStart(2, '0') + " • " + option.quality
                                } else {
                                    item.title + " • " + option.quality
                                }
                                DownloadManagerHolder.addDownload(context, downloadId, option.url, title, item.posterUrl, option.headers)
                                downloadMessage = "Added to Downloads • " + option.quality
                                downloadSelectionInfo = null
                            },
                            onDismiss = { downloadSelectionInfo = null }
                        )
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun DownloadSelectionDialog(
    title: String,
    languages: List<AudioTrackInfo>,
    selectedLanguageId: String,
    options: List<PlaybackOption>,
    initialResolution: Int,
    onLanguageChange: (String) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedResolution by remember { mutableIntStateOf(initialResolution) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var resolutionMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        text = {
            Column {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                Text("Audio / Language", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { languageMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        val selected = languages.firstOrNull { it.subjectId == selectedLanguageId }
                        Text(selected?.label ?: selected?.language ?: "Original")
                    }
                    DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                        if (languages.isEmpty()) {
                            DropdownMenuItem(text = { Text("Original") }, onClick = { languageMenuOpen = false })
                        } else {
                            languages.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.label.ifBlank { language.language.ifBlank { "Original" } }) },
                                    onClick = {
                                        onLanguageChange(language.subjectId)
                                        languageMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Resolution", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { resolutionMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        val selected = options.firstOrNull { it.resolution == selectedResolution }
                        Text(selected?.quality ?: (selectedResolution.toString() + "p"))
                    }
                    DropdownMenu(expanded = resolutionMenuOpen, onDismissRequest = { resolutionMenuOpen = false }) {
                        options.distinctBy { it.resolution }.sortedByDescending { it.resolution }.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.quality.ifBlank { option.resolution.toString() + "p" }) },
                                onClick = {
                                    selectedResolution = option.resolution
                                    resolutionMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedResolution) }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LibraryScreen() {
    var downloads by remember { mutableStateOf<List<LeoDownloadItem>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            downloads = withContext(Dispatchers.IO) { DownloadManagerHolder.downloads(context) }
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
    ) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Offline downloads",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(14.dp))

        if (downloads.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, null)
                Text("No downloads yet. Open a movie or episode and tap Download.")
            }
        } else {
            downloads.forEach { item ->
                DownloadLibraryCard(item)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun DownloadLibraryCard(item: LeoDownloadItem) {
    val context = LocalContext.current
    var playOffline by remember(item.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                item.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            val percent = if (item.percent >= 0f) "${item.percent.toInt()}%" else "Preparing"
            Text(
                when (item.state) {
                    Download.STATE_COMPLETED -> "Completed • Offline ready"
                    Download.STATE_DOWNLOADING -> "Downloading • $percent"
                    Download.STATE_QUEUED -> "Queued • $percent"
                    Download.STATE_STOPPED -> "Paused • $percent"
                    Download.STATE_FAILED -> "Failed"
                    Download.STATE_REMOVING -> "Removing…"
                    else -> percent
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.state == Download.STATE_DOWNLOADING || item.state == Download.STATE_QUEUED) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.state == Download.STATE_COMPLETED) {
                    OutlinedButton(onClick = { playOffline = true }) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Play Offline")
                    }
                } else if (item.state == Download.STATE_DOWNLOADING || item.state == Download.STATE_QUEUED) {
                    OutlinedButton(onClick = { DownloadManagerHolder.pauseDownload(context, item.id) }) {
                        Text("Pause")
                    }
                } else if (item.state == Download.STATE_STOPPED) {
                    OutlinedButton(onClick = { DownloadManagerHolder.resumeDownload(context, item.id) }) {
                        Text("Resume")
                    }
                } else if (item.state == Download.STATE_FAILED) {
                    OutlinedButton(onClick = { DownloadManagerHolder.retryDownload(context, item.id) }) {
                        Text("Retry")
                    }
                }
                OutlinedButton(onClick = { DownloadManagerHolder.removeDownload(context, item.id) }) {
                    Text("Remove")
                }
            }

            if (playOffline && item.state == Download.STATE_COMPLETED) {
                OfflineDownloadPlayer(item.id)
            }
        }
    }
}

@Composable
private fun OfflineDownloadPlayer(id: String) {
    val context = LocalContext.current
    val download = remember(id) {
        runCatching { DownloadManagerHolder.index(context).getDownload(id) }.getOrNull()
    } ?: return

    val player = remember(id) {
        val cacheDataSourceFactory = DownloadManagerHolder.cachedPlaybackDataSourceFactory(context)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
            .apply {
                setMediaItem(download.request.toMediaItem())
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("MovieBox Leo Android", fontWeight = FontWeight.SemiBold)
        Text("Android client • Rust engine bridge • v0.1.0")
        Spacer(Modifier.height(18.dp))
        Text("The Rust engine remains the source of truth for providers, downloads, history, favorites, subtitles and playback resolution.")
    }
}


@Composable
private fun PlaybackPlayer(
    playbackInfo: PlaybackInfo,
    selectedResolution: Int,
    onResolutionChange: (Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var resolutionMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(playbackInfo, selectedResolution) {
        val option = playbackInfo.options.firstOrNull { it.resolution == selectedResolution } ?: playbackInfo.defaultOption
        player?.release()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(option.headers)
        val cleanUrl = option.url.substringBefore('?').lowercase()
        val mimeType = when {
            cleanUrl.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            cleanUrl.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            else -> MimeTypes.VIDEO_MP4
        }
        val mediaItem = MediaItem.Builder()
            .setUri(option.url)
            .setMimeType(mimeType)
            .build()
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(playbackError: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("MovieBoxLeo", "Playback failed: " + playbackError.errorCodeName, playbackError)
                    }
                })
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            player?.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply { useController = true; controllerAutoShow = true } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    FilledTonalButton(onClick = { resolutionMenuOpen = true }) {
                        Text(playbackInfo.options.firstOrNull { it.resolution == selectedResolution }?.quality ?: playbackInfo.defaultOption.quality)
                    }
                    DropdownMenu(expanded = resolutionMenuOpen, onDismissRequest = { resolutionMenuOpen = false }) {
                        playbackInfo.options.distinctBy { it.resolution }.sortedByDescending { it.resolution }.forEach { option ->
                            DropdownMenuItem(text = { Text(option.quality) }, onClick = { onResolutionChange(option.resolution); resolutionMenuOpen = false })
                        }
                    }
                }
                FilledTonalButton(onClick = onClose) { Text("Exit Fullscreen") }
            }
        }
    }
}

