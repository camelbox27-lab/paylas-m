package com.paylasm.iptv.data

import kotlinx.serialization.Serializable

enum class ContentKind { LIVE, MOVIE, SERIES }

data class Category(
    val id: String,
    val name: String
)

/** One row in a channel / movie / series list. */
data class StreamItem(
    val id: String,
    val name: String,
    val image: String?,
    val categoryId: String?,
    val kind: ContentKind,
    /** container_extension, VOD only */
    val ext: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    val num: Int = 0
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val ext: String,
    val image: String?,
    val plot: String?,
    val duration: String?
)

data class SeasonGroup(
    val season: Int,
    val episodes: List<Episode>
)

data class AccountInfo(
    val username: String,
    val status: String,
    val expiry: String,
    val maxConnections: String,
    val activeConnections: String,
    val isTrial: Boolean,
    val serverUrl: String
)

/** Favourites are stored on the device so the tab works without a round trip. */
@Serializable
data class FavItem(
    val kind: String,
    val id: String,
    val name: String,
    val image: String? = null,
    val ext: String? = null
) {
    fun contentKind(): ContentKind = runCatching { ContentKind.valueOf(kind) }.getOrDefault(ContentKind.LIVE)

    fun toStreamItem(): StreamItem = StreamItem(
        id = id,
        name = name,
        image = image,
        categoryId = null,
        kind = contentKind(),
        ext = ext
    )

    companion object {
        fun of(item: StreamItem) = FavItem(item.kind.name, item.id, item.name, item.image, item.ext)
    }
}
