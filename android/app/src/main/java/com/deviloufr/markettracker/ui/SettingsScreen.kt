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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.deviloufr.markettracker.BuildConfig
import com.deviloufr.markettracker.data.AiModels
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
        Text("Réglages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SettingsCard("Seuils de détection") {
            LabeledSlider(
                label = "Variation de prix",
                value = settings.priceChangePct.toFloat(),
                range = 0.5f..15f,
                display = { String.format(Locale.US, "±%.1f%%", it) },
                onCommit = { v -> vm.updateSettings { it.copy(priceChangePct = (v * 10).roundToInt() / 10.0) } }
            )
            LabeledSlider(
                label = "Fenêtre de prix",
                value = settings.priceWindowMinutes.toFloat(),
                range = 1f..30f,
                display = { "${it.roundToInt()} min" },
                onCommit = { v -> vm.updateSettings { it.copy(priceWindowMinutes = v.roundToInt()) } }
            )
            LabeledSlider(
                label = "Pic de volume",
                value = settings.volumeSpikeFactor.toFloat(),
                range = 1.5f..10f,
                display = { String.format(Locale.US, "%.1f×", it) },
                onCommit = { v -> vm.updateSettings { it.copy(volumeSpikeFactor = (v * 10).roundToInt() / 10.0) } }
            )
            LabeledSlider(
                label = "Échantillons moy. volume",
                value = settings.volumeMaPeriods.toFloat(),
                range = 5f..60f,
                display = { "${it.roundToInt()}" },
                onCommit = { v -> vm.updateSettings { it.copy(volumeMaPeriods = v.roundToInt()) } }
            )
        }

        SettingsCard("Cadence") {
            LabeledSlider(
                label = "Intervalle de sondage",
                value = settings.pollIntervalSeconds.toFloat(),
                range = 15f..300f,
                display = { "${it.roundToInt()} s" },
                onCommit = { v -> vm.updateSettings { it.copy(pollIntervalSeconds = v.roundToInt()) } }
            )
            LabeledSlider(
                label = "Délai entre alertes",
                value = settings.cooldownMinutes.toFloat(),
                range = 1f..60f,
                display = { "${it.roundToInt()} min" },
                onCommit = { v -> vm.updateSettings { it.copy(cooldownMinutes = v.roundToInt()) } }
            )
        }

        WhatsAppCard(settings = settings, vm = vm, onTest = { ok ->
            Toast.makeText(
                context,
                if (ok) "Test WhatsApp envoyé ✅" else "Échec — vérifiez le téléphone, la clé et que WhatsApp est activé",
                Toast.LENGTH_LONG
            ).show()
        })

        AiCard(settings = settings, vm = vm)

        SettingsCard("À propos") {
            Text(
                "Les prix proviennent de Yahoo Finance (non officiel, sans clé). La surveillance " +
                    "s'exécute sur l'appareil via un service au premier plan ; désactivez l'optimisation " +
                    "de la batterie pour des alertes fiables en arrière-plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
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
                Text("Alertes WhatsApp (gratuit)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = settings.whatsappEnabled,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(whatsappEnabled = on) } }
                )
            }
            Text(
                "Utilise CallMeBot (gratuit, personnel). Configuration : ajoutez +34 621 331 709 à vos " +
                    "contacts, envoyez-lui « I allow callmebot to send me messages » sur WhatsApp, et il " +
                    "vous répond avec votre clé API.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; vm.updateSettings { s -> s.copy(whatsappPhone = it) } },
                label = { Text("Votre téléphone (ex. +33612345678)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; vm.updateSettings { s -> s.copy(whatsappApiKey = it) } },
                label = { Text("Clé API CallMeBot") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { vm.sendTestWhatsApp(onTest) }) {
                Text("Envoyer un message test")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiCard(settings: Settings, vm: MarketViewModel) {
    var apiKey by remember { mutableStateOf(settings.anthropicApiKey) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Analyse IA (optionnel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "L'analyse technique locale (tendance, RSI, momentum) est toujours gratuite et sans clé. "
                    + "Pour l'analyse approfondie — recherche web des actualités et du sentiment des analystes — "
                    + "renseignez votre propre clé API Anthropic (console.anthropic.com). La clé est stockée sur "
                    + "l'appareil et n'est utilisée qu'à la demande ; chaque analyse coûte quelques centimes sur "
                    + "votre compte. Informatif uniquement, pas un conseil financier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; vm.updateSettings { s -> s.copy(anthropicApiKey = it.trim()) } },
                label = { Text("Clé API Anthropic (sk-ant-…)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Text("Modèle", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AiModels.OPUS, AiModels.SONNET).forEach { id ->
                    FilterChip(
                        selected = settings.aiModel == id,
                        onClick = { vm.updateSettings { it.copy(aiModel = id) } },
                        label = { Text(AiModels.label(id)) }
                    )
                }
            }
        }
    }
}
