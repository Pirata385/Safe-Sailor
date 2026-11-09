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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        thread(name = "blocklist-load") { AdultBlocklist.ensureLoaded(this) }
    }
}
