package com.safesailor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.safesailor.data.AdultBlocklist
import com.safesailor.data.Repo
import kotlin.concurrent.thread

class SafeSailorApplication : Application() {
    companion object {
        const val CHANNEL_PROTECTION = "protection"
        const val CHANNEL_ALERTS = "alerts"
    }

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        val channel = NotificationChannel(
            CHANNEL_PROTECTION, getString(R.string.channel_protection), NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_protection_desc)
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = getString(R.string.channel_alerts_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, alerts))
        thread(name = "blocklist-load") { AdultBlocklist.ensureLoaded(this) }
    }
}
