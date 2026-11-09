package com.safesailor

import com.safesailor.util.DomainUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainUtilsTest {
    @Test fun normalizesUrls() {
        assertEquals("instagram.com", DomainUtils.normalize("https://www.Instagram.com/explore?x=1"))
        assertEquals("m.facebook.com", DomainUtils.normalize("m.facebook.com"))
        assertEquals("example.com.br", DomainUtils.normalize("  http://user@example.com.br:8080/a  "))
        assertEquals("www.com", DomainUtils.normalize("www.com"))
        assertEquals("xn--maana-pta.com", DomainUtils.normalize("mañana.com"))
    }

    @Test fun rejectsInvalid() {
        assertNull(DomainUtils.normalize(""))
        assertNull(DomainUtils.normalize("localhost"))
        assertNull(DomainUtils.normalize("exa mple.com"))
        assertNull(DomainUtils.normalize("-bad-.com"))
        assertNull(DomainUtils.normalize("192.168.0.1"))
    }

    @Test fun parsesMany() {
        assertEquals(
            listOf("tiktok.com", "x.com", "reddit.com"),
            DomainUtils.parseMany("tiktok.com, x.com\nhttps://www.reddit.com/r/a ; invalid tiktok.com"),
        )
    }

    @Test fun matchesSubdomains() {
        val set = setOf("instagram.com")
        assertEquals("instagram.com", DomainUtils.findMatch("scontent.instagram.com", set))
        assertEquals("instagram.com", DomainUtils.findMatch("instagram.com", set))
        assertNull(DomainUtils.findMatch("notinstagram.com", set))
        assertNull(DomainUtils.findMatch("instagram.com.evil.org", set))
    }

    @Test fun rootDomain() {
        assertEquals("cdninstagram.com", DomainUtils.rootDomain("scontent.xx.cdninstagram.com"))
        assertEquals("uol.com.br", DomainUtils.rootDomain("www.uol.com.br"))
        assertEquals("example.com", DomainUtils.rootDomain("example.com"))
    }
}
