package com.paylasm.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paylasm.iptv.data.ALL_CATEGORY
import com.paylasm.iptv.data.ContentKind
import com.paylasm.iptv.data.Repo
import com.paylasm.iptv.data.StreamItem

private enum class Tab(val label: String, val icon: ImageVector, val kind: ContentKind?) {
    LIVE("Canlı TV", Icons.Default.LiveTv, ContentKind.LIVE),
    MOVIES("Filmler", Icons.Default.Movie, ContentKind.MOVIE),
    SERIES("Diziler", Icons.Default.Tv, ContentKind.SERIES),
    FAVORITES("Favoriler", Icons.Default.Favorite, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlay: (StreamItem) -> Unit,
    onOpenSeries: (StreamItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.LIVE) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val account by Repo.account.collectAsState()

    LaunchedEffect(Unit) {
        if (account == null) Repo.refreshAccount()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tab.label, style = MaterialTheme.typography.titleMedium)
                            account?.let {
                                Text(
                                    "Bitiş: ${it.expiry}  •  ${it.activeConnections}/${it.maxConnections} bağlantı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        if (tab != Tab.FAVORITES) {
                            IconButton(onClick = {
                                searching = !searching
                                if (!searching) query = ""
                            }) {
                                Icon(
                                    if (searching) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Ara"
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                        }
                    }
                )
                if (searching && tab.kind != null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("İsme göre ara…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.values().forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            tab = entry
                            searching = false
                            query = ""
                        },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val kind = tab.kind
            if (kind == null) {
                FavoritesTab(onPlay = onPlay, onOpenSeries = onOpenSeries)
            } else {
                CatalogTab(
                    kind = kind,
                    query = query,
                    onPlay = onPlay,
                    onOpenSeries = onOpenSeries
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTab(
    kind: ContentKind,
    query: String,
    onPlay: (StreamItem) -> Unit,
    onOpenSeries: (StreamItem) -> Unit
) {
    val section by Repo.section(kind).collectAsState()
    val favorites by Repo.favorites.collectAsState()

    LaunchedEffect(kind) { Repo.loadCategories(kind) }
    LaunchedEffect(kind, query) {
        if (query.isNotBlank()) Repo.loadItems(kind, ALL_CATEGORY)
    }

    val trimmed = query.trim()
    val items = remember(section, trimmed) {
        if (trimmed.isBlank()) {
            section.visible
        } else {
            section.cache[ALL_CATEGORY].orEmpty()
                .filter { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    val favoriteIds = remember(favorites, kind) {
        favorites.filter { it.kind == kind.name }.map { it.id }.toSet()
    }

    Column(Modifier.fillMaxSize()) {
        if (trimmed.isBlank() && section.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip(
                        label = "Tümü",
                        selected = section.selected == ALL_CATEGORY,
                        onClick = { Repo.select(kind, ALL_CATEGORY) }
                    )
                }
                items(section.categories, key = { it.id }) { category ->
                    CategoryChip(
                        label = category.name,
                        selected = section.selected == category.id,
                        onClick = { Repo.select(kind, category.id) }
                    )
                }
            }
        }

        if (section.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        val error = section.error
        when {
            error != null && items.isEmpty() -> ErrorBox(error) { Repo.retry(kind) }

            items.isEmpty() && section.loading -> LoadingBox()

            items.isEmpty() && trimmed.isNotBlank() ->
                EmptyBox("\"$trimmed\" için sonuç bulunamadı.")

            items.isEmpty() -> EmptyBox("Bu kategoride içerik yok.")

            kind == ContentKind.LIVE -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    ChannelRow(
                        item = item,
                        favorite = item.id in favoriteIds,
                        onClick = { onPlay(item) },
                        onToggleFavorite = { Repo.toggleFavorite(item) }
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        favorite = item.id in favoriteIds,
                        onClick = {
                            if (item.kind == ContentKind.SERIES) onOpenSeries(item) else onPlay(item)
                        },
                        onToggleFavorite = { Repo.toggleFavorite(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    onPlay: (StreamItem) -> Unit,
    onOpenSeries: (StreamItem) -> Unit
) {
    val favorites by Repo.favorites.collectAsState()
    val items = remember(favorites) { favorites.map { it.toStreamItem() } }

    if (items.isEmpty()) {
        EmptyBox(
            "Henüz favori yok.\n\nBir kanalın veya filmin üzerine uzun basarak favorilere ekleyebilirsin."
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { "${it.kind}:${it.id}" }) { item ->
            ChannelRow(
                item = item,
                favorite = true,
                onClick = {
                    if (item.kind == ContentKind.SERIES) onOpenSeries(item) else onPlay(item)
                },
                onToggleFavorite = { Repo.toggleFavorite(item) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
