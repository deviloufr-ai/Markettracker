package com.deviloufr.markettracker.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.deviloufr.markettracker.data.AppRelease
import java.io.File

/** Progress snapshot of the in-flight APK download. */
data class DownloadStatus(
    val done: Boolean,
    val failed: Boolean,
    val progress: Float,   // 0f..1f, or -1f while the total size is unknown
    val fileUri: Uri?,
)

/**
 * Downloads a published APK with the system [DownloadManager] and hands it to the
 * package installer. The file lands in app-specific external storage (no storage
 * permission needed) and is shared to the installer through our [FileProvider].
 */
object UpdateInstaller {
    // Under getExternalFilesDir(null), i.e. .../Android/data/<pkg>/files/updates/…
    private const val REL_PATH = "updates/MarketTracker-update.apk"

    private fun targetFile(context: Context) = File(context.getExternalFilesDir(null), REL_PATH)

    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** Start downloading [release]'s APK; returns the DownloadManager id, or null on failure. */
    fun enqueue(context: Context, release: AppRelease): Long? = try {
        val file = targetFile(context)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()   // avoid DownloadManager's "-1" filename renames
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("MarketTracker ${release.versionName}")
            .setDescription("Téléchargement de la mise à jour…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, REL_PATH)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (e: Exception) {
        null
    }

    /** Poll the download's status/progress. */
    fun query(context: Context, id: Long): DownloadStatus {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                return when (status) {
                    DownloadManager.STATUS_SUCCESSFUL ->
                        DownloadStatus(done = true, failed = false, progress = 1f, fileUri = fileUri(context))
                    DownloadManager.STATUS_FAILED ->
                        DownloadStatus(done = false, failed = true, progress = 0f, fileUri = null)
                    else -> {
                        val p = if (total > 0) soFar.toFloat() / total else -1f
                        DownloadStatus(done = false, failed = false, progress = p, fileUri = null)
                    }
                }
            }
        }
        // No row for this id — treat as failed so the UI doesn't spin forever.
        return DownloadStatus(done = false, failed = true, progress = 0f, fileUri = null)
    }

    private fun fileUri(context: Context): Uri =
        FileProvider.getUriForFile(context, authority(context), targetFile(context))

    /**
     * Launch the package installer for the downloaded APK. On Android O+ the user
     * must first allow this app to install unknown apps; when that isn't granted
     * yet we open that settings screen and return false, so the caller can let the
     * user retry Install afterwards (the APK is already on disk).
     */
    fun install(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return false
        }
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching { context.startActivity(install); true }.getOrDefault(false)
    }
}
