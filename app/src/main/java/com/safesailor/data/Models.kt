package com.safesailor.data

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro") }

enum class DnsProvider(val label: String, val description: String, val primary: String, val secondary: String) {
    CLOUDFLARE_FAMILY("Cloudflare Família", "Bloqueia malware e conteúdo adulto · recomendado", "1.1.1.3", "1.0.0.3"),
    ADGUARD_FAMILY("AdGuard Família", "Bloqueia anúncios, rastreadores e conteúdo adulto", "94.140.14.15", "94.140.15.16"),
    CLEANBROWSING_ADULT("CleanBrowsing Adulto", "Filtro adulto rigoroso", "185.228.168.10", "185.228.169.11"),
    CLOUDFLARE("Cloudflare", "Rápido e privado, sem filtro extra", "1.1.1.1", "1.0.0.1"),
    GOOGLE("Google", "DNS público do Google, sem filtro extra", "8.8.8.8", "8.8.4.4"),
    QUAD9("Quad9", "Bloqueia domínios maliciosos", "9.9.9.9", "149.112.112.112"),
}

data class Settings(
    val protectionEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val adultFilter: Boolean = true,
    val keywordFilter: Boolean = true,
    val safeSearch: Boolean = true,
    val youtubeRestricted: Boolean = false,
    val blockBypass: Boolean = true,
    val dnsProvider: DnsProvider = DnsProvider.CLOUDFLARE_FAMILY,
    val startOnBoot: Boolean = true,
    val logActivity: Boolean = true,
    val logAllowed: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val adultListUpdatedAt: Long = 0L,
    val onboardingDone: Boolean = false,
    val alwaysOnTipDismissed: Boolean = false,
) {
    val hasPin get() = pinHash != null
}

data class SiteEntry(val domain: String, val enabled: Boolean = true, val addedAt: Long = System.currentTimeMillis())

data class Stats(
    val day: String = "",
    val blockedToday: Long = 0,
    val blockedTotal: Long = 0,
    val queriesTotal: Long = 0,
)

enum class LogType(val label: String, val isBlock: Boolean) {
    BLOCKED_USER("Seu bloqueio", true),
    BLOCKED_ADULT("Conteúdo adulto", true),
    BLOCKED_KEYWORD("Palavra-chave", true),
    BLOCKED_BYPASS("Desvio de filtro", true),
    SAFESEARCH("Busca segura", false),
    ALLOWED("Permitido", false),
}

data class LogEntry(val id: Long, val domain: String, val time: Long, val type: LogType)

data class SitePack(val name: String, val domains: List<String>)

object SitePacks {
    val all = listOf(
        SitePack(
            "Redes sociais",
            listOf(
                "facebook.com", "instagram.com", "cdninstagram.com", "tiktok.com", "tiktokv.com", "x.com",
                "twitter.com", "snapchat.com", "kwai.com", "threads.net", "reddit.com", "pinterest.com",
            ),
        ),
        SitePack(
            "Apostas",
            listOf(
                "bet365.com", "bet365.bet.br", "betano.com", "betano.bet.br", "sportingbet.com", "sportingbet.bet.br",
                "superbet.com", "superbet.bet.br", "blaze.com", "pixbet.com", "pixbet.bet.br", "estrelabet.com",
                "estrelabet.bet.br", "betnacional.com", "betnacional.bet.br", "novibet.com", "kto.com", "kto.bet.br",
                "stake.com", "betfair.com", "betway.com", "1xbet.com", "pinnacle.com", "parimatch.com",
                "f12.bet", "vaidebet.com", "esportesdasorte.com", "galera.bet", "mrjack.bet",
            ),
        ),
        SitePack(
            "Jogos online",
            listOf(
                "roblox.com", "rbxcdn.com", "epicgames.com", "fortnite.com", "steampowered.com",
                "steamcommunity.com", "miniclip.com", "poki.com", "friv.com", "crazygames.com",
            ),
        ),
        SitePack(
            "Vídeos e streaming",
            listOf("youtube.com", "youtu.be", "googlevideo.com", "twitch.tv", "kick.com", "netflix.com", "primevideo.com"),
        ),
    )
}
