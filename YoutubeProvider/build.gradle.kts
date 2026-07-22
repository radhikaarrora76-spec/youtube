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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
