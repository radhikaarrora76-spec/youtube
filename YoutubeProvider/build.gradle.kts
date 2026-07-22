version = 3

cloudstream {
    language = "en"
    description = "Watch and stream YouTube videos inside Cloudstream"
    authors = listOf("YourName")
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
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
}
