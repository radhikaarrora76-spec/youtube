package com.yourname.youtubeprovider

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * A Cloudstream provider that lets you search YouTube and stream videos.
 *
 * It goes through a public Invidious instance's JSON API rather than scraping
 * youtube.com directly, since Invidious already does the signature-cipher
 * decoding for us and exposes plain, direct googlevideo.com stream URLs.
 *
 * NOTE: Invidious instances are volunteer-run and go up/down often. If videos
 * stop loading, swap `invidiousInstance` below for another instance from
 * https://api.invidious.io/ (a list of currently healthy instances).
 */
class YoutubeProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Others)
    override val hasDownloadSupport = false

    // Change this to any instance from https://api.invidious.io/ if this one goes down
    private var invidiousInstance = "https://yewtu.be"

    // Many Invidious instances silently reject/rate-limit requests that don't
    // look like they're coming from a real browser.
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    )

    // ---------- Home page ----------
    // Invidious' "trending" endpoint doubles as a simple home page feed.
    override val mainPage = mainPageOf(
        "$invidiousInstance/api/v1/trending?type=all" to "Trending",
        "$invidiousInstance/api/v1/trending?type=music" to "Music",
        "$invidiousInstance/api/v1/trending?type=gaming" to "Gaming",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val videos = app.get(request.data, headers = headers).parsedSafe<List<InvidiousVideo>>() ?: emptyList()
        val items = videos.mapNotNull { it.toSearchResponse(this) }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    // ---------- Search ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$invidiousInstance/api/v1/search?q=$encodedQuery&type=video"
        val results = app.get(url, headers = headers).parsedSafe<List<InvidiousVideo>>() ?: return emptyList()
        return results.mapNotNull { it.toSearchResponse(this) }
    }

    // ---------- Load (video detail page) ----------
    override suspend fun load(url: String): LoadResponse {
        // url is expected to be the videoId, e.g. "dQw4w9WgXcQ"
        val videoId = url.substringAfterLast("/")
        val info = app.get("$invidiousInstance/api/v1/videos/$videoId", headers = headers)
            .parsed<InvidiousVideoDetail>()

        return newMovieLoadResponse(
            name = info.title ?: "Unknown",
            url = videoId,
            type = TvType.Others,
            dataUrl = videoId
        ) {
            this.posterUrl = info.videoThumbnails?.firstOrNull()?.url
            this.plot = info.description
            this.tags = info.keywords
        }
    }

    // ---------- Load stream links ----------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val videoId = data
        val info = app.get("$invidiousInstance/api/v1/videos/$videoId", headers = headers)
            .parsed<InvidiousVideoDetail>()

        var found = false

        // Prefer combined audio+video "formatStreams" - simplest, single URL playback
        info.formatStreams?.forEach { stream ->
            val streamUrl = stream.url ?: return@forEach
            callback(
                newExtractorLink(
                    source = this.name,
                    name = "${this.name} ${stream.qualityLabel ?: ""}".trim(),
                    url = streamUrl,
                    type = ExtractorLinkType.VIDEO
                )
            )
            found = true
        }

        // Fall back to adaptive (video-only) streams if no combined stream exists
        if (!found) {
            info.adaptiveFormats?.filter { it.type?.startsWith("video") == true }
                ?.forEach { stream ->
                    val streamUrl = stream.url ?: return@forEach
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} ${stream.qualityLabel ?: ""}".trim(),
                            url = streamUrl,
                            type = ExtractorLinkType.VIDEO
                        )
                    )
                    found = true
                }
        }

        return found
    }
}

// ---------- Invidious JSON models ----------

data class InvidiousThumbnail(
    @JsonProperty("url") val url: String? = null
)

data class InvidiousVideo(
    @JsonProperty("videoId") val videoId: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("videoThumbnails") val videoThumbnails: List<InvidiousThumbnail>? = null,
) {
    fun toSearchResponse(provider: YoutubeProvider): SearchResponse? {
        val id = videoId ?: return null
        return provider.newMovieSearchResponse(
            name = title ?: "Untitled",
            url = id,
            type = TvType.Others
        ) {
            this.posterUrl = videoThumbnails?.firstOrNull()?.url
        }
    }
}

data class InvidiousFormatStream(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("qualityLabel") val qualityLabel: String? = null,
    @JsonProperty("type") val type: String? = null
)

data class InvidiousVideoDetail(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("keywords") val keywords: List<String>? = null,
    @JsonProperty("videoThumbnails") val videoThumbnails: List<InvidiousThumbnail>? = null,
    @JsonProperty("formatStreams") val formatStreams: List<InvidiousFormatStream>? = null,
    @JsonProperty("adaptiveFormats") val adaptiveFormats: List<InvidiousFormatStream>? = null
)
