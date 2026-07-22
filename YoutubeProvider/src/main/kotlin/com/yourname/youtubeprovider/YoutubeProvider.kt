package com.yourname.youtubeprovider

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.AppUtils.parseJson

class YoutubeProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Others)
    override val hasDownloadSupport = false

    private val invidiousInstances = listOf(
        "https://invidious.nerdvpn.de",
        "https://yt.artemislena.eu",
        "https://invidious.tiekoetter.com",
        "https://vid.puffyan.us",
        "https://yewtu.be"
    )

    private val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    )

    private suspend fun fetchFromInstances(path: String): String? {
        for (instance in invidiousInstances) {
            try {
                val response = app.get("$instance$path", headers = browserHeaders)
                val text = response.text
                if (text.trimStart().startsWith("[") || text.trimStart().startsWith("{")) {
                    return text
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    override val mainPage = mainPageOf(
        "/api/v1/trending?type=all" to "Trending",
        "/api/v1/trending?type=music" to "Music",
        "/api/v1/trending?type=gaming" to "Gaming",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val json = fetchFromInstances(request.data)
            ?: return newHomePageResponse(request.name, emptyList(), hasNext = false)
        val videos = try {
            parseJson<List<InvidiousVideo>>(json)
        } catch (e: Exception) {
            emptyList()
        }
        val items = videos.mapNotNull { it.toSearchResponse(this) }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val json = fetchFromInstances("/api/v1/search?q=$encodedQuery&type=video")
            ?: return emptyList()
        val results = try {
            parseJson<List<InvidiousVideo>>(json)
        } catch (e: Exception) {
            emptyList()
        }
        return results.mapNotNull { it.toSearchResponse(this) }
    }

    override suspend fun load(url: String): LoadResponse {
        val videoId = url.substringAfterLast("/")
        val json = fetchFromInstances("/api/v1/videos/$videoId")
            ?: throw Exception("Could not reach any Invidious instance")
        val info = try {
            parseJson<InvidiousVideoDetail>(json)
        } catch (e: Exception) {
            throw Exception("Could not parse video info")
        }

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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = fetchFromInstances("/api/v1/videos/$data") ?: return false
        val info = try {
            parseJson<InvidiousVideoDetail>(json)
        } catch (e: Exception) {
            return false
        }

        var found = false

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

        if (!found) {
            info.adaptiveFormats
                ?.filter { it.type?.startsWith("video") == true }
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
