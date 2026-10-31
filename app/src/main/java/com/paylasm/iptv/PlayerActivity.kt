package com.paylasm.iptv

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.paylasm.iptv.data.Repo
import com.paylasm.iptv.data.XtreamClient
import com.paylasm.iptv.ui.PaylasMTheme

class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_LIVE = "extra_live"

        fun start(context: Context, url: String, title: String, isLive: Boolean) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_LIVE, isLive)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val isLive = intent.getBooleanExtra(EXTRA_LIVE, false)

        setContent {
            PaylasMTheme {
                PlayerScreen(
                    initialUrl = url,
                    title = title,
                    isLive = isLive,
                    onClose = { finish() }
                )
            }
        }
    }
}

/** ".ts" <-> ".m3u8" — the fix for the overwhelming majority of "channel won't open". */
private fun swapExtension(url: String): String = when {
    url.endsWith(".ts", ignoreCase = true) -> url.dropLast(3) + ".m3u8"
    url.endsWith(".m3u8", ignoreCase = true) -> url.dropLast(5) + ".ts"
    else -> "$url.m3u8"
}

private fun currentExtension(url: String): String =
    if (url.endsWith(".m3u8", ignoreCase = true)) "m3u8" else "ts"

/** Errors where a different container/extension plausibly fixes it. */
private fun looksLikeFormatProblem(error: PlaybackException): Boolean = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> true
    else -> false
}

private fun describe(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "Sunucu yayını reddetti.\nBağlantı limitin dolmuş olabilir (başka cihazda açık?) ya da bu kanal şu an yayında değil."
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "Yayın bulunamadı (404).\nFormat yanlış olabilir — aşağıdan .ts / .m3u8 değiştirmeyi dene."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        "Sunucuya bağlanılamadı.\nİnternet bağlantını kontrol et."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Bağlantı zaman aşımına uğradı.\nSunucu yavaş ya da ulaşılamıyor."
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
        "Şifresiz (http) bağlantıya izin verilmedi."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
        "Yayın biçimi tanınmadı.\nFormatı değiştirmeyi ya da harici oynatıcıyı dene."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED ->
        "Cihaz bu yayının kodeğini çözemedi.\nHarici oynatıcı (VLC / MX Player) daha iyi sonuç verebilir."
    else ->
        "Oynatılamadı.\n(${error.errorCodeName})"
}

private fun openExternally(context: Context, url: String, title: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(url), "video/*")
        .putExtra("title", title)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Harici oynatıcı seç").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

@Composable
private fun PlayerScreen(
    initialUrl: String,
    title: String,
    isLive: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    var url by remember { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var autoSwapTried by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    val exo = remember {
        val dataSource = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(XtreamClient.stream)
                .setUserAgent(Repo.prefs.userAgent)
        )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_000, 40_000, 1_500, 3_000)
                    .build()
            )
            .build()
            .apply { playWhenReady = true }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                buffering = false
                // Try the other container once before bothering the user.
                if (isLive && !autoSwapTried && looksLikeFormatProblem(playbackError)) {
                    autoSwapTried = true
                    url = swapExtension(url)
                    attempt++
                    return
                }
                error = describe(playbackError)
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(url, attempt) {
        error = null
        buffering = true
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    setPlayer(exo)
                    setUseController(true)
                    setControllerShowTimeoutMs(3_500)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        if (buffering && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        title.ifBlank { "Yükleniyor…" },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        error?.let { message ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xE60B0E14))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(22.dp))

                    Button(
                        onClick = { attempt++ },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Tekrar dene") }

                    if (isLive) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                url = swapExtension(url)
                                Repo.prefs.liveExt = currentExtension(url)
                                attempt++
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Formatı değiştir (şu an .${currentExtension(url)})")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { openExternally(context, url, title) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Harici oynatıcıda aç (VLC / MX Player)") }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onClose,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Kapat") }
                }
            }
        }
    }
}
