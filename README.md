# Cloudstream YouTube Provider + Repository

This project builds a Cloudstream **provider** (the "addon") that lets you search
and stream YouTube videos, plus a GitHub Actions pipeline that turns this repo
into an installable Cloudstream **repository**.

## How it works

- `YoutubeProvider/` — the actual plugin code (`YoutubeProvider.kt` + `YoutubePlugin.kt`).
- It fetches video info and direct stream URLs from a public **Invidious**
  instance's API (`https://yewtu.be` by default) instead of scraping YouTube
  directly — Invidious already handles YouTube's signature-cipher decoding.
- `.github/workflows/build.yml` — on every push to `master`, this compiles the
  provider into a `.cs3` file, generates `repo.json`, and publishes both to a
  `builds` branch.
- Cloudstream then points at the raw `repo.json` URL on that `builds` branch.

## One-time setup

1. **Create a GitHub repo** and push this project to it.
2. **Add the Gradle wrapper** (not included here — generate it once locally or
   let Android Studio do it):
   ```bash
   gradle wrapper --gradle-version 8.7
   git add gradlew gradlew.bat gradle/
   git commit -m "Add gradle wrapper"
   git push
   ```
   (If you have Android Studio, just open the project folder and it will offer
   to generate the wrapper for you automatically.)
3. In your repo's **Settings → Actions → General → Workflow permissions**,
   set permissions to **"Read and write permissions"** so the Action is
   allowed to push to the `builds` branch.
4. Edit `com.yourname.youtubeprovider` package name / author name in:
   - `build.gradle.kts` (root, `namespace` line)
   - `YoutubeProvider/build.gradle.kts` (`authors = listOf(...)`)
   - The Kotlin package folder name (optional, cosmetic)
5. Push to `master`. The Action runs, builds the plugin, and creates the
   `builds` branch containing `repo.json` + `YoutubeProvider.cs3`.

## Installing it in Cloudstream

Once the `builds` branch exists, your repo URL is:

```
https://raw.githubusercontent.com/<your-username>/<your-repo>/builds/repo.json
```

In the Cloudstream app: **Settings → Extensions → Add repository** → paste
that URL → open it → install the "YouTube" provider from the list.

## Notes / limitations

- **Invidious instances go down often.** If videos stop loading, open
  `YoutubeProvider.kt` and change `invidiousInstance` to another instance from
  the current healthy list at https://api.invidious.io/.
- This pulls public video streams only — no login, no private/age-restricted
  content, no ad-free bypass of anything paywalled.
- YouTube's own Terms of Service restrict downloading/re-hosting content;
  this project only plays videos back through Cloudstream's player, the same
  way a browser embed would, but you're responsible for how you use it.
- If you want thumbnails/search to hit a *different* Invidious instance than
  the one used for streaming, you can split `invidiousInstance` into two vars.

## Testing locally without the full Android build

Cloudstream has a lightweight test harness (`Blatzar/CloudstreamApi`) that lets
you run `search()` / `load()` / `loadLinks()` as a plain Kotlin `main()`
without building an APK — useful for quickly checking the Invidious JSON
parsing works before you push.
