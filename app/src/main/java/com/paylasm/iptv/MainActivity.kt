package com.paylasm.iptv

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.paylasm.iptv.data.ContentKind
import com.paylasm.iptv.data.Episode
import com.paylasm.iptv.data.Repo
import com.paylasm.iptv.data.StreamItem
import com.paylasm.iptv.ui.HomeScreen
import com.paylasm.iptv.ui.LoginScreen
import com.paylasm.iptv.ui.PaylasMTheme
import com.paylasm.iptv.ui.SeriesDetailScreen
import com.paylasm.iptv.ui.SettingsScreen

private sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data object Settings : Screen
    data class SeriesDetail(val item: StreamItem) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaylasMTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current

    var stack by remember {
        mutableStateOf(listOf<Screen>(if (Repo.prefs.isLoggedIn) Screen.Home else Screen.Login))
    }

    BackHandler(enabled = stack.size > 1) {
        stack = stack.dropLast(1)
    }

    when (val current = stack.last()) {
        Screen.Login -> LoginScreen(
            onLoggedIn = { stack = listOf(Screen.Home) }
        )

        Screen.Home -> HomeScreen(
            onPlay = { item -> playItem(context, item) },
            onOpenSeries = { series -> stack = stack + Screen.SeriesDetail(series) },
            onOpenSettings = { stack = stack + Screen.Settings }
        )

        Screen.Settings -> SettingsScreen(
            onBack = { stack = stack.dropLast(1) },
            onLoggedOut = { stack = listOf(Screen.Login) }
        )

        is Screen.SeriesDetail -> SeriesDetailScreen(
            series = current.item,
            onBack = { stack = stack.dropLast(1) },
            onPlayEpisode = { episode, series -> playEpisode(context, episode, series) }
        )
    }
}

private fun playItem(context: Context, item: StreamItem) {
    val url = Repo.playbackUrl(item)
    if (url == null) {
        Toast.makeText(context, "Yayın adresi oluşturulamadı.", Toast.LENGTH_SHORT).show()
        return
    }
    PlayerActivity.start(context, url, item.name, item.kind == ContentKind.LIVE)
}

private fun playEpisode(context: Context, episode: Episode, series: StreamItem) {
    val url = Repo.episodeUrl(episode)
    if (url == null) {
        Toast.makeText(context, "Bölüm adresi oluşturulamadı.", Toast.LENGTH_SHORT).show()
        return
    }
    PlayerActivity.start(
        context,
        url,
        "${series.name} — S${episode.season}B${episode.episodeNum}",
        isLive = false
    )
}
