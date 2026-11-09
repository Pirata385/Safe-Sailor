package com.safesailor.vpn

import com.safesailor.util.DomainUtils

enum class BlockReason { USER, ADULT_LIST, KEYWORD, BYPASS }

sealed interface Decision {
    data object Allow : Decision
    data class Block(val reason: BlockReason) : Decision
    data class Rewrite(val target: String) : Decision
}

class FilterConfig(
    val userBlocked: Set<String>,
    val allowed: Set<String>,
    val adultFilter: Boolean,
    val adultDomains: Set<String>,
    val keywordFilter: Boolean,
    val safeSearch: Boolean,
    val youtubeRestricted: Boolean,
    val blockBypass: Boolean,
)

object DnsFilter {
    /** Palavras que, se aparecerem no domínio, indicam conteúdo adulto. */
    val ADULT_KEYWORDS = listOf(
        "porn", "xxx", "xvideo", "xnxx", "xhamster", "redtube", "youporn", "hentai",
        "brazzers", "sexcam", "livesex", "camgirl", "nsfw", "putaria", "rule34",
    )

    /** Domínios conhecidos de grande conteúdo adulto (complementam a lista baixada). */
    val CURATED_ADULT = setOf(
        "pornhub.com", "xvideos.com", "xvideos.red", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "spankbang.com", "eporner.com", "erome.com", "redgifs.com", "chaturbate.com",
        "stripchat.com", "bongacams.com", "livejasmin.com", "myfreecams.com", "cam4.com", "camsoda.com",
        "onlyfans.com", "fansly.com", "manyvids.com", "clips4sale.com", "brazzers.com", "nhentai.net",
        "e-hentai.org", "hanime.tv", "rule34.xxx", "brasileirinhas.com.br", "sexyhot.com.br",
        "privacy.com.br", "beeg.com", "tube8.com", "motherless.com", "imagefap.com", "txxx.com",
    )

    /**
     * Servidores de "DNS seguro" (DoH/DoT) usados por navegadores para contornar o filtro.
     * use-application-dns.net é o domínio-canário que faz o Firefox desligar o DoH automático.
     */
    val BYPASS_DOMAINS = setOf(
        "use-application-dns.net", "dns.google", "dns.google.com", "cloudflare-dns.com",
        "one.one.one.one", "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
        "doh.opendns.com", "doh.familyshield.opendns.com", "dns.adguard.com", "dns.adguard-dns.com",
        "family.adguard-dns.com", "unfiltered.adguard-dns.com", "dns.nextdns.io", "doh.cleanbrowsing.org",
        "dns.mullvad.net", "doh.mullvad.net", "dns.controld.com", "freedns.controld.com", "doh.dns.sb",
        "dns.alidns.com", "doh.pub", "dns.switch.ch", "doh.libredns.gr",
    )

    fun decide(rawDomain: String, c: FilterConfig): Decision {
        val domain = rawDomain.lowercase().trimEnd('.')
        if (domain.isEmpty()) return Decision.Allow
        if (DomainUtils.findMatch(domain, c.allowed) != null) return Decision.Allow
        if (DomainUtils.findMatch(domain, c.userBlocked) != null) return Decision.Block(BlockReason.USER)
        if (c.adultFilter) {
            if (DomainUtils.findMatch(domain, c.adultDomains) != null ||
                DomainUtils.findMatch(domain, CURATED_ADULT) != null
            ) return Decision.Block(BlockReason.ADULT_LIST)
            if (c.keywordFilter && ADULT_KEYWORDS.any { domain.contains(it) }) {
                return Decision.Block(BlockReason.KEYWORD)
            }
        }
        if (c.blockBypass && DomainUtils.findMatch(domain, BYPASS_DOMAINS) != null) {
            return Decision.Block(BlockReason.BYPASS)
        }
        SafeSearch.target(domain, c.safeSearch, c.youtubeRestricted)?.let { return Decision.Rewrite(it) }
        return Decision.Allow
    }
}

/** Força a "busca segura" redirecionando o DNS dos buscadores para seus endereços filtrados. */
object SafeSearch {
    private val google = Regex("^(www\\.)?google\\.(com|[a-z]{2}|co\\.[a-z]{2}|com\\.[a-z]{2})$")
    private val bing = setOf("bing.com", "www.bing.com")
    private val ddg = setOf("duckduckgo.com", "www.duckduckgo.com", "start.duckduckgo.com", "html.duckduckgo.com")
    private val youtube = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtubei.googleapis.com",
        "youtube.googleapis.com", "www.youtube-nocookie.com",
    )

    fun target(domain: String, safeSearch: Boolean, youtubeRestricted: Boolean): String? {
        if (safeSearch) {
            if (google.matches(domain)) return "forcesafesearch.google.com"
            if (domain in bing) return "strict.bing.com"
            if (domain in ddg) return "safe.duckduckgo.com"
        }
        if (youtubeRestricted && domain in youtube) return "restrict.youtube.com"
        return null
    }
}
