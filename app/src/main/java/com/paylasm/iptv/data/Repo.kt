package com.paylasm.iptv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val ALL_CATEGORY = "__all__"

data class Section(
    val categories: List<Category> = emptyList(),
    val categoriesLoaded: Boolean = false,
    val selected: String = ALL_CATEGORY,
    /** category id (or ALL_CATEGORY) -> items */
    val cache: Map<String, List<StreamItem>> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val visible: List<StreamItem> get() = cache[selected].orEmpty()
}

/**
 * Single source of truth for the whole app. Kept as an object rather than a
 * ViewModel so that lists survive rotation and tab switches without re-querying
 * the panel, which most providers rate-limit.
 */
object Repo {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var prefs: Prefs
        private set

    var client: XtreamClient? = null
        private set

    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _favorites = MutableStateFlow<List<FavItem>>(emptyList())
    val favorites: StateFlow<List<FavItem>> = _favorites.asStateFlow()

    private val sections: Map<ContentKind, MutableStateFlow<Section>> =
        ContentKind.values().associateWith { MutableStateFlow(Section()) }

    private val inFlight = mutableSetOf<String>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = Prefs(context)
        _favorites.value = prefs.loadFavorites()
        if (prefs.isLoggedIn) rebuildClient()
    }

    fun section(kind: ContentKind): StateFlow<Section> = sections.getValue(kind).asStateFlow()

    private fun rebuildClient() {
        client = XtreamClient(prefs.baseUrl, prefs.username, prefs.password, prefs.userAgent)
    }

    /** Verifies the credentials against the panel before storing them. */
    suspend fun connect(base: String, user: String, pass: String): Result<AccountInfo> = runCatching {
        val candidate = XtreamClient(base, user, pass, prefs.userAgent)
        val info = candidate.login()
        prefs.saveLogin(base, user, pass)
        client = candidate
        _account.value = info
        resetSections()
        info
    }

    fun refreshAccount() {
        val c = client ?: return
        scope.launch { runCatching { _account.value = c.login() } }
    }

    fun logout() {
        prefs.logout()
        client = null
        _account.value = null
        resetSections()
    }

    /** Applied after the user changes the User-Agent in settings. */
    fun applyUserAgentChange() {
        if (prefs.isLoggedIn) {
            rebuildClient()
            resetSections()
        }
    }

    private fun resetSections() {
        inFlight.clear()
        sections.values.forEach { it.value = Section() }
    }

    fun loadCategories(kind: ContentKind) {
        val flow = sections.getValue(kind)
        if (flow.value.categoriesLoaded) return
        val key = "cat:$kind"
        if (!inFlight.add(key)) return

        flow.value = flow.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val c = client ?: throw XtreamException("Oturum açık değil.")
                val list = when (kind) {
                    ContentKind.LIVE -> c.liveCategories()
                    ContentKind.MOVIE -> c.movieCategories()
                    ContentKind.SERIES -> c.seriesCategories()
                }
                flow.value = flow.value.copy(
                    categories = list,
                    categoriesLoaded = true,
                    loading = false,
                    error = null
                )
            } catch (e: Exception) {
                flow.value = flow.value.copy(
                    loading = false,
                    error = e.message ?: "Kategoriler yüklenemedi."
                )
            } finally {
                inFlight.remove(key)
            }
        }
        // The default view is "Tümü", so start fetching it alongside the categories.
        loadItems(kind, ALL_CATEGORY)
    }

    fun select(kind: ContentKind, categoryKey: String) {
        val flow = sections.getValue(kind)
        flow.value = flow.value.copy(selected = categoryKey, error = null)
        loadItems(kind, categoryKey)
    }

    fun loadItems(kind: ContentKind, categoryKey: String, force: Boolean = false) {
        val flow = sections.getValue(kind)
        if (!force && flow.value.cache.containsKey(categoryKey)) return

        val key = "items:$kind:$categoryKey"
        if (!inFlight.add(key)) return

        flow.value = flow.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val c = client ?: throw XtreamException("Oturum açık değil.")
                val categoryId = categoryKey.takeIf { it != ALL_CATEGORY }
                val items = when (kind) {
                    ContentKind.LIVE -> c.liveStreams(categoryId)
                    ContentKind.MOVIE -> c.movieStreams(categoryId)
                    ContentKind.SERIES -> c.seriesList(categoryId)
                }
                flow.value = flow.value.copy(
                    cache = flow.value.cache + (categoryKey to items),
                    loading = false,
                    error = null
                )
            } catch (e: Exception) {
                flow.value = flow.value.copy(
                    loading = false,
                    error = e.message ?: "Liste yüklenemedi."
                )
            } finally {
                inFlight.remove(key)
            }
        }
    }

    fun retry(kind: ContentKind) {
        val flow = sections.getValue(kind)
        val current = flow.value
        flow.value = current.copy(error = null, categoriesLoaded = false)
        inFlight.clear()
        loadCategories(kind)
        loadItems(kind, current.selected, force = true)
    }

    /* ---- favourites ---- */

    fun isFavorite(item: StreamItem): Boolean =
        _favorites.value.any { it.id == item.id && it.kind == item.kind.name }

    fun toggleFavorite(item: StreamItem) {
        val current = _favorites.value
        val existing = current.firstOrNull { it.id == item.id && it.kind == item.kind.name }
        val updated = if (existing != null) current - existing else current + FavItem.of(item)
        _favorites.value = updated
        prefs.saveFavorites(updated)
    }

    fun clearFavorites() {
        _favorites.value = emptyList()
        prefs.saveFavorites(emptyList())
    }

    /* ---- playback ---- */

    fun playbackUrl(item: StreamItem, liveExtOverride: String? = null): String? {
        val c = client ?: return null
        return when (item.kind) {
            ContentKind.LIVE -> c.liveUrl(item.id, liveExtOverride ?: prefs.liveExt)
            ContentKind.MOVIE -> c.movieUrl(item.id, item.ext ?: "mp4")
            ContentKind.SERIES -> null
        }
    }

    fun episodeUrl(episode: Episode): String? = client?.episodeUrl(episode.id, episode.ext)
}
