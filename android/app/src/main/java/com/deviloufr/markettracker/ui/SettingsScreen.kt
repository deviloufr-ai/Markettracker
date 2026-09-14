package com.deviloufr.markettracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.data.Settings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MarketViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SettingsCard("Detection thresholds") {
            LabeledSlider(
                label = "Price move",
                value = settings.priceChangePct.toFloat(),
                range = 0.5f..15f,
                display = { String.format(Locale.US, "±%.1f%%", it) },
                onCommit = { v -> vm.updateSettings { it.copy(priceChangePct = (v * 10).roundToInt() / 10.0) } }
            )
            LabeledSlider(
                label = "Price window",
                value = settings.priceWindowMinutes.toFloat(),
                range = 1f..30f,
                display = { "${it.roundToInt()} min" },
                onCommit = { v -> vm.updateSettings { it.copy(priceWindowMinutes = v.roundToInt()) } }
            )
            LabeledSlider(
                label = "Volume spike",
                value = settings.volumeSpikeFactor.toFloat(),
                range = 1.5f..10f,
                display = { String.format(Locale.US, "%.1f×", it) },
                onCommit = { v -> vm.updateSettings { it.copy(volumeSpikeFactor = (v * 10).roundToInt() / 10.0) } }
            )
            LabeledSlider(
                label = "Volume avg. samples",
                value = settings.volumeMaPeriods.toFloat(),
                range = 5f..60f,
                display = { "${it.roundToInt()}" },
                onCommit = { v -> vm.updateSettings { it.copy(volumeMaPeriods = v.roundToInt()) } }
            )
        }

        SettingsCard("Timing") {
            LabeledSlider(
                label = "Poll interval",
                value = settings.pollIntervalSeconds.toFloat(),
                range = 15f..300f,
                display = { "${it.roundToInt()} s" },
                onCommit = { v -> vm.updateSettings { it.copy(pollIntervalSeconds = v.roundToInt()) } }
            )
            LabeledSlider(
                label = "Alert cooldown",
                value = settings.cooldownMinutes.toFloat(),
                range = 1f..60f,
                display = { "${it.roundToInt()} min" },
                onCommit = { v -> vm.updateSettings { it.copy(cooldownMinutes = v.roundToInt()) } }
            )
        }

        WhatsAppCard(settings = settings, vm = vm, onTest = { ok ->
            Toast.makeText(
                context,
                if (ok) "WhatsApp test sent ✅" else "Failed — check phone, key & that WhatsApp is enabled",
                Toast.LENGTH_LONG
            ).show()
        })

        SettingsCard("About") {
            Text(
                "Prices come from Yahoo Finance (unofficial, no key). Monitoring runs on-device " +
                    "as a foreground service; keep battery optimisation off for reliable background alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display(local), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            onValueChangeFinished = { onCommit(local) }
        )
    }
}

@Composable
private fun WhatsAppCard(settings: Settings, vm: MarketViewModel, onTest: (Boolean) -> Unit) {
    var phone by remember { mutableStateOf(settings.whatsappPhone) }
    var apiKey by remember { mutableStateOf(settings.whatsappApiKey) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WhatsApp alerts (free)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = settings.whatsappEnabled,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(whatsappEnabled = on) } }
                )
            }
            Text(
                "Uses CallMeBot (free, personal). Setup: add +34 621 331 709 to contacts, send it " +
                    "\"I allow callmebot to send me messages\" on WhatsApp, and it replies with your API key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; vm.updateSettings { s -> s.copy(whatsappPhone = it) } },
                label = { Text("Your phone (e.g. +15551234567)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; vm.updateSettings { s -> s.copy(whatsappApiKey = it) } },
                label = { Text("CallMeBot API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { vm.sendTestWhatsApp(onTest) }) {
                Text("Send test message")
            }
        }
    }
}
