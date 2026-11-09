package com.safesailor.guard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.safesailor.data.Repo
import com.safesailor.ui.PinUnlockActivity

/**
 * Serviço de acessibilidade que protege o app contra desinstalação: quando uma tela do
 * sistema permite desinstalar/desligar o Safe Sailor e o PIN não foi digitado há pouco,
 * volta da tela e pede o PIN. Não guarda nem envia nada.
 */
class UninstallGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    private var lastBlock = 0L

    override fun onServiceConnected() {
        Repo.init(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || !GuardRules.isWatchedPackage(pkg)) return
        if (!Repo.settings.value.hasPin || Repo.isUnlocked()) return

        val now = SystemClock.uptimeMillis()
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastCheck < 250) return
        lastCheck = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collectTexts(root, texts, intArrayOf(0))
        if (!GuardRules.isProtectedScreen(texts)) return
        if (now - lastBlock < 1500) return
        lastBlock = now

        // Sai da tela (fecha o diálogo de desinstalação / volta da página do app)
        // e então mostra o pedido de PIN por cima.
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            try {
                startActivity(
                    Intent(this, PinUnlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            } catch (_: Exception) {
            }
        }, 200)
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, count: IntArray) {
        if (count[0]++ > 400) return
        node.text?.let { out.add(it.toString()) }
        node.contentDescription?.let { out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, out, count) }
        }
    }

    override fun onInterrupt() {}

    companion object {
        /** Se o serviço está ligado nas configurações de acessibilidade. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, UninstallGuardService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
