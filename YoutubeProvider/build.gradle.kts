// Every time you push, this version auto-increments in the builds branch,
// but bump it yourself if you want a clean manual release.
version = 1

cloudstream {
    // All of these properties are optional, remove ones you don't need

    description = "Watch and stream YouTube videos inside Cloudstream"
    authors = listOf("YourName")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    tvTypes = listOf("Others")

    iconUrl = "https://www.google.com/s2/favicons?domain=youtube.com&sz=%size%"
}

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    val implementation by configurations
    // NiceHttp - provides the `app` object and `app.get(...)` used in the provider
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    // Needed for @JsonProperty on the data classes used to parse Invidious' JSON responses
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
}
