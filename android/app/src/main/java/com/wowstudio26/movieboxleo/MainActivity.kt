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
                    0 -> HomeScreen(query, { query = it })
                    1 -> SearchScreen(query, { query = it })
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
private fun HomeScreen(query: String, onQueryChange: (String) -> Unit) {
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
            placeholder = { Text("Search movies, series, anime…") }
        )

        Spacer(Modifier.height(24.dp))
        demoRows.forEach { (title, items) ->
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            MediaRow(items)
            Spacer(Modifier.height(22.dp))
        })
    }
}

@Composable
private fun SearchScreen(query: String, onQueryChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            placeholder = { Text("Search…") }
        )
        Spacer(Modifier.height(24.dp))
        Text(
            if (query.isBlank()) "Start typing to search the available providers."
            else "Provider search will be connected to the Rust core in the next integration stage.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaRow(items: List<MediaItem>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 18.dp)
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier
                    .height(150.dp)
                    .clickable { },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF252530), Color(0xFF15151A))
                                )
                            )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(item.meta, style = MaterialTheme.typography.bodySmall)
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
        Text("Android client foundation • v0.1.0")
        Spacer(Modifier.height(18.dp))
        Text("The existing Rust engine remains the source of truth for providers, downloads, history, favorites, subtitles and playback resolution.")
    }
}
