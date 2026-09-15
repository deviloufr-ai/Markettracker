package com.deviloufr.markettracker.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens BoursoBank so the user can place a real order there — the only legitimate
 * way, since BoursoBank exposes no third-party order API. We launch the installed
 * app when present (it lands on its own home; banking apps publish no deep link to
 * a pre-filled order ticket), and otherwise open the bourse website in a browser.
 * Declared in the manifest `<queries>` so package visibility works on Android 11+.
 */
object BoursoBank {

    const val PACKAGE = "com.boursorama.android.clients"
    private const val BOURSE_URL = "https://www.boursobank.com/bourse/"

    /** True when the BoursoBank app is installed and launchable. */
    fun isAppInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    /** Launch the app if installed, else the bourse website. Returns false if nothing could open. */
    fun open(context: Context): Boolean {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(launch); true }.getOrDefault(false)
        }
        return openWeb(context)
    }

    fun openWeb(context: Context, url: String = BOURSE_URL): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
