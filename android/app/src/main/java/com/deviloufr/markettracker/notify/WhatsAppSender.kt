package com.deviloufr.markettracker.notify

import com.deviloufr.markettracker.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Free WhatsApp notifications via CallMeBot (personal use).
 * Setup: message the CallMeBot WhatsApp number to obtain an API key, then set
 * the phone + key in Settings. See the in-app instructions.
 */
object WhatsAppSender {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Returns true if the message was accepted by CallMeBot. */
    suspend fun send(settings: Settings, text: String): Boolean = withContext(Dispatchers.IO) {
        if (!settings.whatsappEnabled) return@withContext false
        val phone = settings.whatsappPhone.trim()
        val key = settings.whatsappApiKey.trim()
        if (phone.isEmpty() || key.isEmpty()) return@withContext false

        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://api.callmebot.com/whatsapp.php?phone=$phone&text=$encoded&apikey=$key"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { resp -> resp.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
