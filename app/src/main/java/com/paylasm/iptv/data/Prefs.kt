package com.paylasm.iptv.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("paylasm_iptv", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    var baseUrl: String
        get() = sp.getString(KEY_BASE, "").orEmpty()
        set(v) { sp.edit().putString(KEY_BASE, v).apply() }

    var username: String
        get() = sp.getString(KEY_USER, "").orEmpty()
        set(v) { sp.edit().putString(KEY_USER, v).apply() }

    var password: String
        get() = sp.getString(KEY_PASS, "").orEmpty()
        set(v) { sp.edit().putString(KEY_PASS, v).apply() }

    /** "ts" or "m3u8" — the single most common cause of a channel not playing. */
    var liveExt: String
        get() = sp.getString(KEY_LIVE_EXT, DEFAULT_LIVE_EXT).orEmpty().ifBlank { DEFAULT_LIVE_EXT }
        set(v) { sp.edit().putString(KEY_LIVE_EXT, v).apply() }

    /** Some panels reject unknown clients, so this is user-editable. */
    var userAgent: String
        get() = sp.getString(KEY_UA, DEFAULT_UA).orEmpty().ifBlank { DEFAULT_UA }
        set(v) { sp.edit().putString(KEY_UA, v).apply() }

    val isLoggedIn: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank()

    fun saveLogin(base: String, user: String, pass: String) {
        sp.edit()
            .putString(KEY_BASE, base)
            .putString(KEY_USER, user)
            .putString(KEY_PASS, pass)
            .apply()
    }

    fun logout() {
        sp.edit().remove(KEY_BASE).remove(KEY_USER).remove(KEY_PASS).apply()
    }

    fun loadFavorites(): List<FavItem> {
        val raw = sp.getString(KEY_FAVS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<FavItem>>(raw) }.getOrDefault(emptyList())
    }

    fun saveFavorites(items: List<FavItem>) {
        sp.edit().putString(KEY_FAVS, json.encodeToString(items)).apply()
    }

    private companion object {
        const val KEY_BASE = "base_url"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        const val KEY_LIVE_EXT = "live_ext"
        const val KEY_UA = "user_agent"
        const val KEY_FAVS = "favorites"

        const val DEFAULT_LIVE_EXT = "ts"
        const val DEFAULT_UA = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
