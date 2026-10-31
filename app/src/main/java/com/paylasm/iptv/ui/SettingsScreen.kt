package com.paylasm.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paylasm.iptv.data.Repo

private val USER_AGENTS = listOf(
    "VLC/3.0.20 LibVLC/3.0.20",
    "Lavf/60.16.100",
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
    "okhttp/4.12.0"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val prefs = Repo.prefs
    val account by Repo.account.collectAsState()
    val favorites by Repo.favorites.collectAsState()

    var liveExt by remember { mutableStateOf(prefs.liveExt) }
    var userAgent by remember { mutableStateOf(prefs.userAgent) }
    var saved by remember { mutableStateOf(false) }

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
                title = { Text("Ayarlar", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsCard("Hesap") {
                account?.let { info ->
                    InfoRow("Kullanıcı", info.username)
                    InfoRow("Durum", info.status)
                    InfoRow("Bitiş tarihi", info.expiry)
                    InfoRow("Bağlantı", "${info.activeConnections} / ${info.maxConnections}")
                    if (info.isTrial) InfoRow("Tip", "Deneme hesabı")
                } ?: Text(
                    "Hesap bilgisi alınamadı.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                InfoRow("Sunucu", prefs.baseUrl)
            }

            Spacer(Modifier.height(14.dp))

            SettingsCard("Canlı yayın formatı") {
                Text(
                    "Kanallar açılmıyor ya da \"bulunamadı\" hatası veriyorsa diğerini dene. " +
                        "Sunucuların çoğu .ts kullanır, bazıları yalnızca .m3u8 verir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ts", "m3u8").forEach { option ->
                        FilterChip(
                            selected = liveExt == option,
                            onClick = {
                                liveExt = option
                                prefs.liveExt = option
                            },
                            shape = RoundedCornerShape(10.dp),
                            label = { Text(".$option") },
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

            Spacer(Modifier.height(14.dp))

            SettingsCard("Tarayıcı kimliği (User-Agent)") {
                Text(
                    "Bazı sunucular tanımadıkları uygulamaları engeller. Liste boş geliyorsa " +
                        "başka bir kimlik seçip tekrar dene.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it; saved = false },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                USER_AGENTS.forEach { preset ->
                    OutlinedButton(
                        onClick = { userAgent = preset; saved = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            preset.take(42),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        prefs.userAgent = userAgent.trim().ifBlank { USER_AGENTS.first() }
                        Repo.applyUserAgentChange()
                        saved = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saved) "Kaydedildi ✓" else "Kaydet ve listeleri yenile")
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsCard("Favoriler") {
                InfoRow("Kayıtlı", "${favorites.size} içerik")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { Repo.clearFavorites() },
                    enabled = favorites.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Favorileri temizle")
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    Repo.logout()
                    onLoggedOut()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Çıkış yap")
            }

            Spacer(Modifier.height(24.dp))

            Box(Modifier.fillMaxWidth()) {
                Text(
                    "Paylas-M IPTV 1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
