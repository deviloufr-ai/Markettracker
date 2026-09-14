package com.deviloufr.markettracker

import android.app.Application
import com.deviloufr.markettracker.notify.Notifier

class MarketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
