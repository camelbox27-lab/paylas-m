package com.paylasm.iptv.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Carries a message that is already fit to show to the user, in Turkish. */
class XtreamException(message: String) : IOException(message)

/* --------------------------------------------------------------------------
 * Xtream panels are wildly inconsistent about JSON types: stream_id comes back
 * as a number on one server and a string on the next, empty lists arrive as
 * {} instead of [], and missing values are sometimes the string "null".
 * Everything is therefore read through these tolerant helpers instead of
 * strict @Serializable classes, which is what normally makes these apps
 * explode on a particular provider.
 * -------------------------------------------------------------------------- */

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

internal fun JsonObject.str(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    val content = primitive.content
    return if (content.isBlank() || content == "null") null else content
}

internal fun JsonObject.int(key: String): Int? =
    str(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

class XtreamClient(
    val base: String,
    private val username: String,
    private val password: String,
    private val userAgent: String
) {

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** Short-lived API calls. */
        val api: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        /**
         * Playback client. Deliberately has NO call timeout: a live stream is one
         * HTTP response that never ends, and a call timeout would cut it off.
         */
        val stream: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private fun query(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Path segments need %20, not '+', so URLEncoder is wrong here. */
    private fun path(value: String): String = Uri.encode(value, "")

    private fun apiUrl(action: String?, extra: Map<String, String>): String {
        val sb = StringBuilder(base)
            .append("/player_api.php?username=").append(query(username))
            .append("&password=").append(query(password))
        if (action != null) sb.append("&action=").append(action)
        extra.forEach { (k, v) -> sb.append('&').append(k).append('=').append(query(v)) }
        return sb.toString()
    }

    private suspend fun call(action: String?, extra: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(apiUrl(action, extra))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val body = try {
                api.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 ->
                            throw XtreamException("Kullanıcı adı veya şifre reddedildi (HTTP ${response.code}).")
                        response.code == 404 ->
                            throw XtreamException("Adres bulunamadı (404). Sunucu adresinin sonunda fazladan bir yol olabilir.")
                        response.code == 512 || response.code == 509 ->
                            throw XtreamException("Sunucu bağlantı limitini aştın (HTTP ${response.code}). Başka cihazda açık olabilir.")
                        !response.isSuccessful ->
                            throw XtreamException("Sunucu hata verdi: HTTP ${response.code}")
                        else -> response.body?.string().orEmpty()
                    }
                }
            } catch (e: XtreamException) {
                throw e
            } catch (e: UnknownHostException) {
                throw XtreamException("Sunucu adresi bulunamadı. Adresi ve internet bağlantını kontrol et.")
            } catch (e: SocketTimeoutException) {
                throw XtreamException("Sunucu yanıt vermedi (zaman aşımı).")
            } catch (e: ConnectException) {
                throw XtreamException("Sunucuya bağlanılamadı. Port numarası doğru mu?")
            } catch (e: SSLException) {
                throw XtreamException("Güvenli bağlantı kurulamadı. Adreste 'https' yerine 'http' dene.")
            } catch (e: IOException) {
                throw XtreamException("Ağ hatası: ${e.message ?: "bilinmiyor"}")
            }

            if (body.isBlank()) throw XtreamException("Sunucu boş yanıt döndü.")

            val head = body.trimStart()
            if (!head.startsWith("{") && !head.startsWith("[")) {
                throw XtreamException(
                    "Sunucu JSON yerine başka bir şey döndürdü. Bu adres bir Xtream paneli olmayabilir.\n\n" +
                        "Gelen yanıt: ${head.take(80)}"
                )
            }

            runCatching { json.parseToJsonElement(body) }
                .getOrElse { throw XtreamException("Sunucu yanıtı çözümlenemedi.") }
        }

    suspend fun login(): AccountInfo {
        val root = call(null).obj()
            ?: throw XtreamException("Beklenmeyen sunucu yanıtı.")
        val info = root["user_info"].obj()
            ?: throw XtreamException("Panel bilgisi alınamadı. Sunucu adresi doğru mu?")

        if (info.str("auth") == "0") {
            throw XtreamException("Giriş reddedildi: kullanıcı adı veya şifre hatalı.")
        }

        val status = info.str("status") ?: "-"
        when (status.lowercase()) {
            "expired" -> throw XtreamException("Aboneliğin süresi dolmuş (Expired).")
            "banned" -> throw XtreamException("Hesap yasaklanmış (Banned).")
            "disabled" -> throw XtreamException("Hesap devre dışı (Disabled).")
        }

        val expiry = info.str("exp_date")?.toLongOrNull()?.let {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it * 1000L))
        } ?: "Sınırsız"

        val server = root["server_info"].obj()

        return AccountInfo(
            username = info.str("username") ?: username,
            status = status,
            expiry = expiry,
            maxConnections = info.str("max_connections") ?: "-",
            activeConnections = info.str("active_cons") ?: "0",
            isTrial = info.str("is_trial") == "1",
            serverUrl = server?.let { s ->
                val host = s.str("url") ?: ""
                val port = s.str("port")
                if (host.isBlank()) base else if (port != null) "$host:$port" else host
            } ?: base
        )
    }

    private suspend fun categories(action: String): List<Category> =
        call(action).arr().mapNotNull { element ->
            val o = element.obj() ?: return@mapNotNull null
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name") ?: "Kategori $id")
        }

    suspend fun liveCategories(): List<Category> = categories("get_live_categories")
    suspend fun movieCategories(): List<Category> = categories("get_vod_categories")
    suspend fun seriesCategories(): List<Category> = categories("get_series_categories")

    private fun categoryParam(categoryId: String?): Map<String, String> =
        if (categoryId.isNullOrBlank()) emptyMap() else mapOf("category_id" to categoryId)

    suspend fun liveStreams(categoryId: String?): List<StreamItem> =
        call("get_live_streams", categoryParam(categoryId)).arr().mapNotNull { element ->
            val o = element.obj() ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            StreamItem(
                id = id,
                name = o.str("name") ?: "Kanal $id",
                image = o.str("stream_icon"),
                categoryId = o.str("category_id"),
                kind = ContentKind.LIVE,
                num = o.int("num") ?: 0
            )
        }

    suspend fun movieStreams(categoryId: String?): List<StreamItem> =
        call("get_vod_streams", categoryParam(categoryId)).arr().mapNotNull { element ->
            val o = element.obj() ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            StreamItem(
                id = id,
                name = o.str("name") ?: "Film $id",
                image = o.str("stream_icon") ?: o.str("cover"),
                categoryId = o.str("category_id"),
                kind = ContentKind.MOVIE,
                ext = o.str("container_extension") ?: "mp4",
                rating = o.str("rating"),
                num = o.int("num") ?: 0
            )
        }

    suspend fun seriesList(categoryId: String?): List<StreamItem> =
        call("get_series", categoryParam(categoryId)).arr().mapNotNull { element ->
            val o = element.obj() ?: return@mapNotNull null
            val id = o.str("series_id") ?: return@mapNotNull null
            StreamItem(
                id = id,
                name = o.str("name") ?: "Dizi $id",
                image = o.str("cover"),
                categoryId = o.str("category_id"),
                kind = ContentKind.SERIES,
                rating = o.str("rating"),
                plot = o.str("plot"),
                num = o.int("num") ?: 0
            )
        }

    suspend fun seriesInfo(seriesId: String): List<SeasonGroup> {
        val root = call("get_series_info", mapOf("series_id" to seriesId)).obj()
            ?: throw XtreamException("Dizi bilgisi alınamadı.")

        val collected = mutableListOf<Episode>()
        when (val episodes = root["episodes"]) {
            // Usual shape: { "1": [ ... ], "2": [ ... ] }
            is JsonObject -> episodes.forEach { (seasonKey, value) ->
                val season = seasonKey.toIntOrNull() ?: 0
                value.arr().forEach { ep -> parseEpisode(ep, season)?.let(collected::add) }
            }
            // Some panels send an array indexed by season instead.
            is JsonArray -> episodes.forEachIndexed { index, value ->
                value.arr().forEach { ep -> parseEpisode(ep, index)?.let(collected::add) }
            }
            else -> Unit
        }

        if (collected.isEmpty()) throw XtreamException("Bu dizi için bölüm listesi boş geldi.")

        return collected
            .groupBy { it.season }
            .toSortedMap()
            .map { (season, list) -> SeasonGroup(season, list.sortedBy { it.episodeNum }) }
    }

    private fun parseEpisode(element: JsonElement, fallbackSeason: Int): Episode? {
        val o = element.obj() ?: return null
        val id = o.str("id") ?: return null
        val info = o["info"].obj()
        return Episode(
            id = id,
            title = o.str("title") ?: "Bölüm ${o.str("episode_num") ?: id}",
            season = o.int("season") ?: fallbackSeason,
            episodeNum = o.int("episode_num") ?: 0,
            ext = o.str("container_extension") ?: "mp4",
            image = info?.str("movie_image") ?: info?.str("cover_big"),
            plot = info?.str("plot"),
            duration = info?.str("duration")
        )
    }

    /** Extra details for a movie (plot, poster, year). Never fatal — returns null on failure. */
    suspend fun movieDetails(streamId: String): StreamItem? = runCatching {
        val root = call("get_vod_info", mapOf("vod_id" to streamId)).obj() ?: return null
        val info = root["info"].obj()
        val movie = root["movie_data"].obj()
        StreamItem(
            id = streamId,
            name = movie?.str("name") ?: info?.str("name") ?: "",
            image = info?.str("movie_image") ?: info?.str("cover_big"),
            categoryId = movie?.str("category_id"),
            kind = ContentKind.MOVIE,
            ext = movie?.str("container_extension") ?: "mp4",
            rating = info?.str("rating"),
            plot = info?.str("plot") ?: info?.str("description")
        )
    }.getOrNull()

    fun liveUrl(streamId: String, ext: String): String =
        "$base/live/${path(username)}/${path(password)}/$streamId.$ext"

    fun movieUrl(streamId: String, ext: String): String =
        "$base/movie/${path(username)}/${path(password)}/$streamId.$ext"

    fun episodeUrl(episodeId: String, ext: String): String =
        "$base/series/${path(username)}/${path(password)}/$episodeId.$ext"
}
