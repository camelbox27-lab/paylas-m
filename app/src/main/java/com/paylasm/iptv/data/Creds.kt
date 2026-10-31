package com.paylasm.iptv.data

import java.net.URI
import java.net.URLDecoder

/**
 * Accepts anything a provider is likely to hand out and digs the three things we
 * need out of it:
 *
 *   http://host:8080
 *   host:8080
 *   http://host:8080/c/
 *   http://host:8080/get.php?username=U&password=P&type=m3u_plus
 *   http://host:8080/live/U/P/1234.ts
 *
 * Getting this wrong is the usual reason a login "just fails", so it is handled
 * up front rather than asking the user to clean the link by hand.
 */
object Creds {

    data class Parsed(val base: String, val user: String?, val pass: String?)

    fun parse(raw: String): Parsed? {
        var s = raw.trim().trim('"', '\'', '<', '>')
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "http://$s"

        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val scheme = (uri.scheme ?: "http").lowercase()
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val base = "$scheme://$host$port"

        var user: String? = null
        var pass: String? = null

        uri.rawQuery?.split('&')?.forEach { part ->
            val i = part.indexOf('=')
            if (i > 0) {
                val key = part.substring(0, i).lowercase()
                val value = dec(part.substring(i + 1))
                when (key) {
                    "username", "user" -> user = value
                    "password", "pass" -> pass = value
                }
            }
        }

        if (user.isNullOrBlank()) {
            val segs = (uri.rawPath ?: "").split('/').filter { it.isNotBlank() }.map { dec(it) }
            val marker = segs.indexOfFirst { it.lowercase() in setOf("live", "movie", "series") }
            if (marker >= 0 && segs.size >= marker + 3) {
                user = segs[marker + 1]
                pass = segs[marker + 2]
            } else if (segs.size >= 3 && segs.last().matches(Regex("""\d+(\.\w+)?"""))) {
                user = segs[segs.size - 3]
                pass = segs[segs.size - 2]
            }
        }

        return Parsed(
            base = base,
            user = user?.takeIf { it.isNotBlank() },
            pass = pass?.takeIf { it.isNotBlank() }
        )
    }

    private fun dec(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
