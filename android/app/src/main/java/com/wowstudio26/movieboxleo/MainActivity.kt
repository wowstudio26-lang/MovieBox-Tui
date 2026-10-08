package com.wowstudio26.movieboxleo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class MediaItem(
    val title: String,
    val meta: String
)

private val demoRows = listOf(
    "Trending" to listOf(
        MediaItem("MovieBox Leo", "Android edition"),
        MediaItem("Continue Watching", "Resume playback"),
        MediaItem("New Releases", "Latest sources")
    ),
    "Popular" to listOf(
        MediaItem("Movies", "Browse movies"),
        MediaItem("Series", "Browse TV series"),
        MediaItem("Anime", "Browse anime")
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MovieBoxLeoTheme {
                MovieBoxLeoApp()
            }
        }
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { selectedTab = 1 }
                    )
                    1 -> SearchScreen(
                        query = query,
                        onQueryChange = { query = it }
                    )
                    2 -> LibraryScreen()
                    else -> SettingsScreen()
                }
            }

            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Download, null) },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("MovieBox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("LEO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search movies, series, anime…") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSearch() }
            )
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Your Android client is now connected to the Rust engine.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Text("Next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Search a title to query the existing MovieBox provider through Rust.")
    }
}

@Composable
private fun SearchScreen(query: String, onQueryChange: (String) -> Unit) {
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
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                RustBridge.search(cleanQuery)
            }
            result.onSuccess {
                results = it
            }.onFailure {
                results = emptyList()
                error = it.message ?: "Search failed"
            }
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search…") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { performSearch() }
            )
        )

        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(
            onClick = { performSearch() },
            enabled = query.isNotBlank() && !searching,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (searching) "Searching…" else "Search")
        }

        Spacer(Modifier.height(18.dp))

        when {
            error != null -> Text(
                error!!,
                color = MaterialTheme.colorScheme.error
            )
            searching -> Text(
                "Searching MovieBox provider…",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            results.isEmpty() && query.isNotBlank() -> Text(
                "No results found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            results.isEmpty() -> Text(
                "Enter a title and search.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                results.forEach { result ->
                    SearchResultCard(result)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(92.dp)
                    .fillMaxWidth(0.26f)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF252530), Color(0xFF15151A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("POSTER", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(1.dp))
            Column(
                modifier = Modifier.padding(start = 14.dp)
            ) {
                Text(result.title, fontWeight = FontWeight.SemiBold)
                if (result.year.isNotBlank()) {
                    Text(result.year, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    result.mediaType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
            Text("Favorites and downloads will use the existing Rust data model.")
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("MovieBox Leo Android", fontWeight = FontWeight.SemiBold)
        Text("Android client • Rust engine bridge • v0.1.0")
        Spacer(Modifier.height(18.dp))
        Text("The Rust engine remains the source of truth for providers, downloads, history, favorites, subtitles and playback resolution.")
    }
}
