package com.safesailor

import com.safesailor.vpn.BlockReason
import com.safesailor.vpn.Decision
import com.safesailor.vpn.DnsFilter
import com.safesailor.vpn.FilterConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsFilterTest {
    private fun config(
        blocked: Set<String> = emptySet(),
        allowed: Set<String> = emptySet(),
        adult: Boolean = true,
        keywords: Boolean = true,
        safe: Boolean = true,
        youtube: Boolean = false,
        bypass: Boolean = true,
    ) = FilterConfig(blocked, allowed, adult, setOf("adultsite.example"), keywords, safe, youtube, bypass)

    @Test fun userBlockIncludesSubdomains() {
        val c = config(blocked = setOf("tiktok.com"))
        assertEquals(Decision.Block(BlockReason.USER), DnsFilter.decide("www.tiktok.com", c))
        assertEquals(Decision.Block(BlockReason.USER), DnsFilter.decide("TikTok.com.", c))
        assertEquals(Decision.Allow, DnsFilter.decide("example.org", c))
    }

    @Test fun allowListWins() {
        val c = config(blocked = setOf("example.com"), allowed = setOf("example.com"))
        assertEquals(Decision.Allow, DnsFilter.decide("a.example.com", c))
    }

    @Test fun adultFilters() {
        assertEquals(Decision.Block(BlockReason.ADULT_LIST), DnsFilter.decide("cdn.adultsite.example", config()))
        assertEquals(Decision.Block(BlockReason.ADULT_LIST), DnsFilter.decide("www.pornhub.com", config()))
        assertEquals(Decision.Block(BlockReason.KEYWORD), DnsFilter.decide("free-porn-videos.net", config()))
        assertEquals(Decision.Allow, DnsFilter.decide("free-porn-videos.net", config(keywords = false)))
        assertEquals(Decision.Allow, DnsFilter.decide("www.pornhub.com", config(adult = false)))
    }

    @Test fun safeSearchRewrites() {
        assertEquals(Decision.Rewrite("forcesafesearch.google.com"), DnsFilter.decide("www.google.com.br", config()))
        assertEquals(Decision.Rewrite("strict.bing.com"), DnsFilter.decide("www.bing.com", config()))
        assertEquals(Decision.Allow, DnsFilter.decide("mail.google.com", config()))
        assertEquals(Decision.Allow, DnsFilter.decide("www.youtube.com", config()))
        assertEquals(Decision.Rewrite("restrict.youtube.com"), DnsFilter.decide("www.youtube.com", config(youtube = true)))
    }

    @Test fun bypassBlocked() {
        assertEquals(Decision.Block(BlockReason.BYPASS), DnsFilter.decide("use-application-dns.net", config()))
        assertEquals(Decision.Block(BlockReason.BYPASS), DnsFilter.decide("mozilla.cloudflare-dns.com", config()))
        assertEquals(Decision.Allow, DnsFilter.decide("dns.google", config(bypass = false)))
    }
}
