# MarketTracker — Android app

A standalone native Android app (Kotlin + Jetpack Compose) that monitors your
watchlist **on-device**, fires **native notifications** on price/volume
anomalies, and can also send **free WhatsApp alerts** via CallMeBot. No server,
no API key required.

## Get the APK (no Android Studio needed)

The app is built for you in the cloud by GitHub Actions:

1. Open the repo's **Actions** tab → **Build Android APK** → the latest run.
2. Download the **MarketTracker-debug-apk** artifact (a zip).
3. Unzip and copy `app-debug.apk` to your phone; tap it to install
   (you'll need to allow "install unknown apps" for your file manager once).

To rebuild after changes, push to `main` (or run the workflow manually via
**Run workflow**).

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
