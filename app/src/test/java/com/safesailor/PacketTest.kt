package com.safesailor

import com.safesailor.vpn.DnsMessage
import com.safesailor.vpn.IpPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketTest {
    private val dnsQuery = DnsMessage.buildQuery(0x1234, "www.Example.com", DnsMessage.TYPE_A)

    private fun ipv4Packet(payload: ByteArray, dstPort: Int = 53): ByteArray {
        val p = ByteArray(28 + payload.size)
        p[0] = 0x45
        IpPacket.put16(p, 2, p.size)
        p[8] = 64
        p[9] = 17
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(p, 12)
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(p, 16)
        IpPacket.put16(p, 20, 40000)
        IpPacket.put16(p, 22, dstPort)
        IpPacket.put16(p, 24, 8 + payload.size)
        payload.copyInto(p, 28)
        return p
    }

    private fun ipv6Packet(payload: ByteArray): ByteArray {
        val p = ByteArray(48 + payload.size)
        p[0] = 0x60
        IpPacket.put16(p, 4, 8 + payload.size)
        p[6] = 17
        p[7] = 64
        p[8 + 15] = 1
        p[24 + 15] = 2
        IpPacket.put16(p, 40, 40001)
        IpPacket.put16(p, 42, 53)
        IpPacket.put16(p, 44, 8 + payload.size)
        payload.copyInto(p, 48)
        return p
    }

    @Test fun parsesQuestion() {
        val q = DnsMessage.parseQuestion(dnsQuery)!!
        assertEquals("www.example.com", q.name)
        assertEquals(DnsMessage.TYPE_A, q.type)
        assertEquals(dnsQuery.size, q.end)
    }

    @Test fun ipv4RoundTrip() {
        val pkt = ipv4Packet(dnsQuery)
        val q = IpPacket.parseDnsQuery(pkt, pkt.size)!!
        assertArrayEquals(dnsQuery, q.dns)
        val question = DnsMessage.parseQuestion(q.dns)!!
        val resp = IpPacket.buildResponse(q, DnsMessage.nxdomain(q.dns, question))

        // Cabeçalho IP válido (checksum soma zero) e endereços invertidos
        assertEquals(0, IpPacket.finish(IpPacket.sum(resp, 0, 20)))
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 2), resp.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 1), resp.copyOfRange(16, 20))
        assertEquals(53, IpPacket.u16(resp, 20))
        assertEquals(40000, IpPacket.u16(resp, 22))
        // Checksum UDP com pseudo-cabeçalho
        val udpLen = resp.size - 20
        val pseudo = IpPacket.sum(resp, 12, 8) + 17 + udpLen + IpPacket.sum(resp, 20, udpLen)
        assertEquals(0, IpPacket.finish(pseudo))
        // Resposta DNS: mesmo ID, QR=1, RCODE=3
        val dns = resp.copyOfRange(28, resp.size)
        assertEquals(0x1234, IpPacket.u16(dns, 0))
        assertEquals(0x80, dns[2].toInt() and 0x80)
        assertEquals(3, dns[3].toInt() and 0x0F)
    }

    @Test fun ipv6RoundTrip() {
        val pkt = ipv6Packet(dnsQuery)
        val q = IpPacket.parseDnsQuery(pkt, pkt.size)!!
        val question = DnsMessage.parseQuestion(q.dns)!!
        val resp = IpPacket.buildResponse(q, DnsMessage.empty(q.dns, question))
        assertEquals(0x60, resp[0].toInt() and 0xF0)
        assertEquals(2, resp[8 + 15].toInt())
        assertEquals(1, resp[24 + 15].toInt())
        val udpLen = resp.size - 40
        val pseudo = IpPacket.sum(resp, 8, 32) + 17 + udpLen + IpPacket.sum(resp, 40, udpLen)
        assertEquals(0, IpPacket.finish(pseudo))
    }

    @Test fun ignoresNonDns() {
        val pkt = ipv4Packet(dnsQuery, dstPort = 443)
        assertNull(IpPacket.parseDnsQuery(pkt, pkt.size))
        assertNull(IpPacket.parseDnsQuery(ByteArray(10), 10))
    }

    @Test fun addressAnswerRoundTrip() {
        val question = DnsMessage.parseQuestion(dnsQuery)!!
        val addrs = listOf(byteArrayOf(216.toByte(), 239.toByte(), 38, 120))
        val resp = DnsMessage.addressResponse(dnsQuery, question, addrs)
        assertEquals(1, IpPacket.u16(resp, 6))
        val extracted = DnsMessage.extractAddresses(resp, DnsMessage.TYPE_A)
        assertEquals(1, extracted.size)
        assertArrayEquals(addrs[0], extracted[0])
        assertNotNull(DnsMessage.extractAddresses(byteArrayOf(1, 2, 3), DnsMessage.TYPE_A))
    }
}
