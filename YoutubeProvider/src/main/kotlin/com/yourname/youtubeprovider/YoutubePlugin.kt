package com.yourname.youtubeprovider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class YoutubePlugin : BasePlugin() {
    override fun load() {
        // Registers the provider so it shows up as a source inside Cloudstream
        registerMainAPI(YoutubeProvider())
    }
}
