package com.deviloufr.markettracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.AppRelease
import com.deviloufr.markettracker.update.UpdateState

/**
 * Top-of-app banner shown when a newer build is available. Offers **Télécharger**
 * (download + install) and **Infos** (opens a closable modal listing the changes),
 * and reflects download progress / install-ready / failure states. Renders nothing
 * when there's no update or the user has closed it for this build.
 */
@Composable
fun UpdateBanner(vm: MarketViewModel) {
    val state by vm.update.collectAsState()
    val dismissed by vm.updateDismissed.collectAsState()
    var showNotes by remember { mutableStateOf(false) }

    val release: AppRelease? = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ReadyToInstall -> s.release
        is UpdateState.Failed -> s.release
        UpdateState.Idle -> null
    }
    if (release == null || dismissed) return

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Mise à jour disponible",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        bannerSubtitle(state, release),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Close the banner (only when nothing is in flight).
                if (state is UpdateState.Available || state is UpdateState.Failed) {
                    IconButton(onClick = { vm.dismissUpdate() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer")
                    }
                }
            }

            if (state is UpdateState.Downloading) {
                val p = (state as UpdateState.Downloading).progress
                if (p >= 0f) {
                    LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is UpdateState.Available ->
                        OutlinedButton(onClick = { vm.downloadUpdate() }) { Text("Télécharger") }
                    is UpdateState.Downloading ->
                        OutlinedButton(onClick = {}, enabled = false) { Text("Téléchargement…") }
                    is UpdateState.ReadyToInstall ->
                        OutlinedButton(onClick = { vm.installReady() }) { Text("Installer") }
                    is UpdateState.Failed ->
                        OutlinedButton(onClick = { vm.downloadUpdate() }) { Text("Réessayer") }
                    UpdateState.Idle -> Unit
                }
                OutlinedButton(onClick = { showNotes = true }) { Text("Infos") }
            }
        }
    }

    if (showNotes) {
        ChangesDialog(release = release, onDismiss = { showNotes = false })
    }
}

private fun bannerSubtitle(state: UpdateState, release: AppRelease): String = when (state) {
    is UpdateState.Downloading -> {
        val p = state.progress
        if (p >= 0f) "Téléchargement… ${(p * 100).toInt()} %" else "Téléchargement…"
    }
    is UpdateState.ReadyToInstall -> "Prêt à installer — v${release.versionName}"
    is UpdateState.Failed -> state.message
    else -> "Version ${release.versionName} (build ${release.versionCode})"
}

/** Closable modal listing the changes shipped in [release] (the manifest's notes). */
@Composable
private fun ChangesDialog(release: AppRelease, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Nouveautés — v${release.versionName}") },
        text = {
            val notes = release.notes.ifBlank { "Aucune note de version fournie." }
            Text(
                notes,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
    )
}
