package com.yourname.youtubeprovider

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * A Cloudstream provider that lets you search YouTube and stream videos.
 *
 * It goes through public Invidious instances' JSON API rather than scraping
 * youtube.com directly, since Invidious already does the signature-cipher
 * decoding for us and exposes plain, direct googlevideo.com stream URLs.
 */
class YoutubeProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Others)
    override val hasDownloadSupport = false

    // Public Invidious instances to try, in order. Many sit behind anti-bot
    // protection that silently returns an HTML challenge page instead of JSON,
    // so we try a few candidates rather than hardcoding one that might be
    // blocking us today. Swap/reorder from https://api.invidious.io/ as needed.
    private val invidiousInstances = listOf(
        "https://invidious.nerdvpn.de",
        "https://yt.artemislena.eu",
        "https://invidious.tiekoetter.com",
        "https://vid.puffyan.us",
        "https://yewtu.be"
    )

    // Many Invidious instances silently reject/rate-limit requests that don't
    // look like they're coming from a real browser.
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    )

    // Tries each Invidious instance in order for a given API path, returning
    // the first one that successfully parses as T. Returns null if all fail.
    private suspend inline fun <reified T : Any> tryInstances(path: String): T? {
        for (instance in invidiousInstances) {
            val result = app.get("$instance$path", headers = headers).parsedSafe<T>()
            if (result != null) return result
        }
        return null
    }

    // ---------- Home page ----------
    // Invidious' "trending" endpoint doubles as a simple home page feed.
    // Paths only (no host) since we try multiple instances per request.
    override val mainPage = mainPageOf(
        "/api/v1/trending?type=all" to "Trending",
        "/api/v1/trending?type=music" to "Music",
        "/api/v1/trending?type=gaming" to "Gaming",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val videos = tryInstances<List<InvidiousVideo>>(request.data) ?: emptyList()
        val items = videos.mapNotNull { it.toSearchResponse(this) }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    // ---------- Search ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = tryInstances<List<InvidiousVideo>>("/api/v1/search?q=$encodedQuery&type=video")
            ?: return emptyList()
        return results.mapNotNull { it.toSearchResponse(this) }
    }

    // ---------- Load (video detail page) ----------
    override suspend fun load(url: String): LoadResponse {
        // url is expected to be the videoId, e.g. "dQw4w9WgXcQ"
        val videoId = url.substringAfterLast("/")
        val info = tryInstances<InvidiousVideoDetail>("/api/v1/videos/$videoId")
            ?: throw Exception("Could not reach any Invidious instance")

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
        val info = tryInstances<InvidiousVideoDetail>("/api/v1/videos/$videoId") ?: return false

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
