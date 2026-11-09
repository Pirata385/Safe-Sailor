package com.safesailor.guard

/**
 * Decide se uma tela do sistema permite desinstalar ou desligar o Safe Sailor.
 * Lógica pura (sem Android) para poder ser testada.
 */
object GuardRules {
    const val APP_LABEL = "Safe Sailor"

    /** Apps do sistema onde ficam as telas perigosas (variam entre fabricantes). */
    private val watchedPackageHints = listOf(
        "settings", "packageinstaller", "vpndialogs", "permissioncontroller",
        "securitycenter", "systemmanager", "safecenter",
    )

    /**
     * Ações que, junto com o nome do app na tela, indicam perigo: desinstalar, forçar parada,
     * limpar dados, desconectar a VPN, desligar a "VPN sempre ativa" ou o serviço de acessibilidade.
     */
    private val keywords = listOf(
        "desinstalar", "uninstall", "forçar parada", "forçar interrupção", "forçar encerramento", "force stop",
        "desativar", "deactivate", "desconectar", "disconnect", "esquecer vpn", "forget vpn",
        "vpn sempre ativa", "always-on vpn", "limpar armazenamento", "limpar dados", "clear storage",
        "clear data", "usar safe sailor", "use safe sailor", "usar serviço", "use service", "parar", "stop",
    )

    // Palavra inteira (sem letras coladas antes/depois), entendendo acentos.
    private val keywordRegex = Regex(
        "(?<!\\p{L})(" + keywords.joinToString("|") { Regex.escape(it) } + ")(?!\\p{L})",
    )

    fun isWatchedPackage(pkg: String): Boolean = watchedPackageHints.any { pkg.contains(it) }

    fun isProtectedScreen(texts: List<String>): Boolean {
        val joined = texts.joinToString("\n").lowercase()
        if (!joined.contains(APP_LABEL.lowercase())) return false
        return keywordRegex.containsMatchIn(joined)
    }
}
