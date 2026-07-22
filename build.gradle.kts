import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
        // The Cloudstream Gradle plugin - handles .cs3 packaging + repo.json generation.
        // Pinned to a commit hash because "master-SNAPSHOT" no longer resolves on JitPack.
        classpath("com.github.recloudstream:gradle:81b1d424d2")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Helper so we can write `cloudstream { ... }` inside subprojects below
fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // This tells the plugin which GitHub repo (owner/repo) it's building for,
        // which it needs to construct the .cs3 download URL in plugins.json.
        // GITHUB_REPOSITORY is set automatically by GitHub Actions.
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "radhikaarrora76-spec/youtube")
    }

    extensions.configure<BaseExtension> {
        namespace = "com.yourname.${project.name.lowercase()}"
        compileSdkVersion(34)

        defaultConfig {
            minSdk = 21
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    dependencies {
        val implementation by configurations
        val cloudstream by configurations
        cloudstream("com.lagradost:cloudstream3:pre-release")
        implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
