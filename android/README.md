# MarketTracker — Android app

A standalone native Android app (Kotlin + Jetpack Compose) that monitors your
watchlist **on-device**, fires **native notifications** on price/volume
anomalies, and can also send **free WhatsApp alerts** via CallMeBot. No server,
no API key required.

## Get the APK (no Android Studio needed)

The app is built for you in the cloud by GitHub Actions on every push to `main`
(or via **Run workflow**). Once the **in-app updates** channel is set up (below),
the phone just installs the first APK and updates itself from then on.

First install, from the repo's Releases:

1. Open `https://github.com/deviloufr-ai/Markettracker/releases/latest`.
2. Download **MarketTracker.apk** and tap it to install (allow "install unknown
   apps" once when prompted).

Fallback (a one-off build, or while the repo is still private): open the repo's
**Actions** tab → **Build Android APK** → the latest run → download the
**MarketTracker-debug-apk** artifact, unzip, and install `app-debug.apk`.

## In-app updates

The app checks for a newer build on launch (and via **Settings → Rechercher une
mise à jour**). When one is available a banner appears with **Télécharger**
(download + install) and **Infos** (a modal listing the changes). It downloads
the new APK and hands it to Android's installer — because every build is signed
with the same key, it installs in place over the current version.

Version is a single source of truth: bump `versionName` in
[`app/build.gradle`](app/build.gradle); `versionCode` is the CI run number, baked
into both the APK and the published `latest.json` manifest so the app can tell
when a newer build exists.

### One-time setup (publisher side)

The app downloads updates over plain HTTPS, so the release assets must be
**publicly** reachable. That means the repo needs to be **public** (no secrets
are committed — `*.keystore` and `.env` are git-ignored, and CI secrets like
`SIGNING_KEYSTORE_B64` stay secret regardless of visibility):

- **Settings → General → Danger Zone → Change repository visibility → Public.**

That's the only step — no token needed. On every push, **Build Android APK**
publishes a GitHub Release on this repo with two assets, `MarketTracker.apk` and
`latest.json`, using the built-in `GITHUB_TOKEN`. The app reads them from the
stable `releases/latest/download/…` URLs. (Releases are created even while the
repo is private; they just aren't downloadable until it's public.)

## First run

1. Allow notifications when prompted (Android 13+).
2. On the **Watchlist** tab, add tickers and tap **Start** to begin monitoring.
   A persistent "Monitoring" notification shows it's running.
3. For reliable background alerts, disable battery optimisation for MarketTracker
   (Settings → Apps → MarketTracker → Battery → Unrestricted).

## WhatsApp alerts (free, optional)

Uses [CallMeBot](https://www.callmebot.com/blog/free-api-whatsapp-messages/):

1. Add **+34 621 331 709** to your contacts.
2. Send it `I allow callmebot to send me messages` on WhatsApp.
3. It replies with your **API key**.
4. In the app's **Settings** tab: enable WhatsApp, enter your phone
   (`+<country><number>`) and the API key, then **Send test message**.

## How it works

- **Data**: Yahoo Finance chart endpoint (unofficial, no key; includes a
  per-minute volume bar).
- **Monitoring**: a foreground `Service` polls each ticker on your configured
  interval, keeps a short in-memory history, and runs the detection rules.
- **Detection**: price move ≥ X% over a sliding window; volume ≥ N× its moving
  average. Thresholds and cadence are editable in **Settings**; a per-ticker
  cooldown prevents spam.
- **Alerts**: high-priority native notification + optional WhatsApp message; the
  **Alerts** tab keeps a history.

## Build locally (optional)

Requires JDK 17 + Android SDK (via Android Studio). Open the `android/` folder in
Android Studio and Run, or from a shell:

```bash
cd android
./gradlew assembleDebug   # or: gradle assembleDebug
```

Package: `com.deviloufr.markettracker` · minSdk 26 · targetSdk 34.
