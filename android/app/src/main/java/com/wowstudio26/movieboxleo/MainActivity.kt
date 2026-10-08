package com.wowstudio26.movieboxleo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
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
    val scope = rememberCoroutineScope()

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

                    Spacer(Modifier.height(20.dp))
                    if (playbackInfo == null) {
                        Button(
                            onClick = {
                                if (playing) return@Button
                                playing = true
                                error = null
                                scope.launch {
                                    val playback =
                                        withContext(Dispatchers.IO) { RustBridge.playback(result.id) }
                                    playback.onSuccess { info ->
                                        playbackInfo = info
                                    }.onFailure {
                                        error = it.message ?: "Unable to resolve stream"
                                    }
                                    playing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (playing) "Resolving stream…" else "Play")
                        }
                    } else {
                        PlaybackPlayer(playbackInfo!!)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { playbackInfo = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close Player")
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen() {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.FavoriteBorder, null)
            Text("Favorites and downloads will use the Rust data model.")
        }
    }
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
private fun PlaybackPlayer(info: PlaybackInfo) {
    val context = LocalContext.current
    val player = remember(info.url, info.headers) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(info.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(info.url))
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
            .height(230.dp)
    )
}
