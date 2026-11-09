package com.safesailor

import com.safesailor.guard.GuardRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardRulesTest {
    private fun blocked(vararg texts: String) = GuardRules.isProtectedScreen(texts.toList())

    @Test fun blocksDangerousScreens() {
        assertTrue(blocked("Informações do app", "Safe Sailor", "Desinstalar", "Forçar parada"))
        assertTrue(blocked("Safe Sailor", "Deseja desinstalar este app?", "Cancelar", "OK"))
        assertTrue(blocked("App info", "Safe Sailor", "Uninstall", "Force stop"))
        assertTrue(blocked("Safe Sailor", "Usar Safe Sailor", "Atalho do Safe Sailor"))
        assertTrue(blocked("Safe Sailor", "Usar serviço"))
        assertTrue(blocked("Parar o Safe Sailor?", "Cancelar", "Parar"))
        assertTrue(blocked("Safe Sailor", "VPN sempre ativa", "Bloquear conexões sem VPN", "Esquecer VPN"))
        assertTrue(blocked("Safe Sailor", "Conectado", "Desconectar"))
        assertTrue(blocked("SAFE SAILOR", "Limpar armazenamento"))
    }

    @Test fun allowsHarmlessScreens() {
        assertFalse(blocked("Apps", "Chrome", "Safe Sailor", "WhatsApp", "Ver todos os 45 apps"))
        assertFalse(blocked("Acessibilidade", "Safe Sailor", "Desativado", "Remover animações"))
        assertFalse(blocked("VPN", "Safe Sailor", "Conectado"))
        assertFalse(blocked("WhatsApp", "Deseja desinstalar este app?"))
        assertFalse(blocked("Safe Sailor", "Deseja atualizar este app?", "Atualizar"))
        assertFalse(blocked("Safe Sailor", "Notificações", "Proteção", "Alertas"))
    }

    @Test fun watchesSystemPackagesOnly() {
        assertTrue(GuardRules.isWatchedPackage("com.android.settings"))
        assertTrue(GuardRules.isWatchedPackage("com.google.android.packageinstaller"))
        assertTrue(GuardRules.isWatchedPackage("com.android.vpndialogs"))
        assertFalse(GuardRules.isWatchedPackage("com.whatsapp"))
        assertFalse(GuardRules.isWatchedPackage("com.android.chrome"))
    }
}
