package com.deviloufr.markettracker.update

import android.net.Uri
import com.deviloufr.markettracker.data.AppRelease

/** UI-facing state machine for the in-app updater, driven from [MarketViewModel]. */
sealed interface UpdateState {
    /** No newer build known (or not yet checked). */
    data object Idle : UpdateState

    /** A newer build is available and can be downloaded. */
    data class Available(val release: AppRelease) : UpdateState

    /** APK download in flight. [progress] is 0f..1f, or -1f when the size is unknown. */
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState

    /** APK downloaded and handed to the installer; kept so Install can be retried. */
    data class ReadyToInstall(val release: AppRelease, val fileUri: Uri) : UpdateState

    /** Download failed; the banner offers a retry. */
    data class Failed(val release: AppRelease, val message: String) : UpdateState
}
