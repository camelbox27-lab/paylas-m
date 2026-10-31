package com.paylasm.iptv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.paylasm.iptv.data.ContentKind
import com.paylasm.iptv.data.StreamItem

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String = "Yükleniyor…") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            if (onRetry != null) {
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Tekrar dene")
                }
            }
        }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Artwork(
    url: String?,
    kind: ContentKind,
    modifier: Modifier = Modifier
) {
    val fallback = when (kind) {
        ContentKind.LIVE -> Icons.Default.LiveTv
        ContentKind.MOVIE -> Icons.Default.Movie
        ContentKind.SERIES -> Icons.Default.PlayArrow
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                fallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                error = {
                    Icon(
                        fallback,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            )
        }
    }
}

/** Live TV: a dense list row, which is what people actually scan channels in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(
    item: StreamItem,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onToggleFavorite)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(
            item.image,
            item.kind,
            Modifier
                .size(width = 58.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.num > 0) {
                Text(
                    "No ${item.num}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favori",
                tint = if (favorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Movies / series: poster grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: StreamItem,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onToggleFavorite)
            .padding(bottom = 6.dp)
    ) {
        Box {
            Artwork(
                item.image,
                item.kind,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
            )
            if (favorite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC0B0E14))
                        .padding(5.dp)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (!item.rating.isNullOrBlank() && item.rating != "0") {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC0B0E14))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "★ ${item.rating}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun SectionSpacer() = Spacer(Modifier.height(10.dp))
