package com.paylasm.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.paylasm.iptv.data.Episode
import com.paylasm.iptv.data.Repo
import com.paylasm.iptv.data.SeasonGroup
import com.paylasm.iptv.data.StreamItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    series: StreamItem,
    onBack: () -> Unit,
    onPlayEpisode: (Episode, StreamItem) -> Unit
) {
    var seasons by remember { mutableStateOf<List<SeasonGroup>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(series.id, reloadToken) {
        loading = true
        error = null
        val client = Repo.client
        if (client == null) {
            error = "Oturum açık değil."
            loading = false
            return@LaunchedEffect
        }
        runCatching { client.seriesInfo(series.id) }.fold(
            onSuccess = {
                seasons = it
                selectedSeason = it.firstOrNull()?.season
                loading = false
            },
            onFailure = {
                error = it.message ?: "Bölümler yüklenemedi."
                loading = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Text(
                        series.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                loading -> LoadingBox(label = "Bölümler yükleniyor…")
                error != null -> ErrorBox(error!!) { reloadToken++ }
                else -> {
                    val current = seasons.firstOrNull { it.season == selectedSeason } ?: seasons.firstOrNull()
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            SeriesHeader(series)
                        }
                        if (seasons.size > 1) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(seasons, key = { it.season }) { group ->
                                        FilterChip(
                                            selected = group.season == current?.season,
                                            onClick = { selectedSeason = group.season },
                                            shape = RoundedCornerShape(10.dp),
                                            label = { Text("Sezon ${group.season}") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        items(current?.episodes.orEmpty(), key = { it.id }) { episode ->
                            EpisodeRow(episode) { onPlayEpisode(episode, series) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(series: StreamItem) {
    Row(Modifier.fillMaxWidth().padding(14.dp)) {
        Box(
            Modifier
                .width(104.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!series.image.isNullOrBlank()) {
                AsyncImage(
                    model = series.image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                series.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!series.rating.isNullOrBlank() && series.rating != "0") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "★ ${series.rating}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!series.plot.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    series.plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 76.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!episode.image.isNullOrBlank()) {
                AsyncImage(
                    model = episode.image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${episode.episodeNum}. ${episode.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!episode.duration.isNullOrBlank()) {
                Text(
                    episode.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
