package com.safesailor.vpn

/** Uma consulta DNS (UDP porta 53) extraída de um pacote IPv4/IPv6 lido da interface VPN. */
class DnsQueryPacket(
    val version: Int,
    val src: ByteArray,
    val dst: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dns: ByteArray,
)

object IpPacket {
    private const val UDP = 17

    fun parseDnsQuery(buf: ByteArray, len: Int): DnsQueryPacket? {
        if (len < 1) return null
        val version = (buf[0].toInt() shr 4) and 0x0F
        val udp: Int
        val src: ByteArray
        val dst: ByteArray
        when (version) {
            4 -> {
                if (len < 28) return null
                val ihl = (buf[0].toInt() and 0x0F) * 4
                if (ihl < 20 || len < ihl + 8) return null
                if ((buf[9].toInt() and 0xFF) != UDP) return null
                if ((u16(buf, 6) and 0x3FFF) != 0) return null // fragmentado
                src = buf.copyOfRange(12, 16)
                dst = buf.copyOfRange(16, 20)
                udp = ihl
            }
            6 -> {
                if (len < 48) return null
                if ((buf[6].toInt() and 0xFF) != UDP) return null
                src = buf.copyOfRange(8, 24)
                dst = buf.copyOfRange(24, 40)
                udp = 40
            }
            else -> return null
        }
        val srcPort = u16(buf, udp)
        val dstPort = u16(buf, udp + 2)
        val udpLen = u16(buf, udp + 4)
        if (dstPort != 53 || udpLen < 8) return null
        val start = udp + 8
        val end = minOf(len, udp + udpLen)
        if (end <= start) return null
        return DnsQueryPacket(version, src, dst, srcPort, dstPort, buf.copyOfRange(start, end))
    }

    /** Monta o pacote de resposta (origem/destino invertidos) contendo [payload] DNS. */
    fun buildResponse(q: DnsQueryPacket, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        return if (q.version == 4) {
            val p = ByteArray(20 + udpLen)
            p[0] = 0x45
            put16(p, 2, p.size)
            p[6] = 0x40 // Don't fragment
            p[8] = 64 // TTL
            p[9] = UDP.toByte()
            System.arraycopy(q.dst, 0, p, 12, 4)
            System.arraycopy(q.src, 0, p, 16, 4)
            put16(p, 10, finish(sum(p, 0, 20)))
            writeUdp(p, 20, q, payload)
            put16(p, 26, udpChecksum(p, q.dst, q.src, 20, udpLen))
            p
        } else {
            val p = ByteArray(40 + udpLen)
            p[0] = 0x60
            put16(p, 4, udpLen)
            p[6] = UDP.toByte()
            p[7] = 64 // hop limit
            System.arraycopy(q.dst, 0, p, 8, 16)
            System.arraycopy(q.src, 0, p, 24, 16)
            writeUdp(p, 40, q, payload)
            put16(p, 46, udpChecksum(p, q.dst, q.src, 40, udpLen))
            p
        }
    }

    private fun writeUdp(p: ByteArray, off: Int, q: DnsQueryPacket, payload: ByteArray) {
        put16(p, off, q.dstPort)
        put16(p, off + 2, q.srcPort)
        put16(p, off + 4, 8 + payload.size)
        System.arraycopy(payload, 0, p, off + 8, payload.size)
    }

    private fun udpChecksum(p: ByteArray, src: ByteArray, dst: ByteArray, off: Int, udpLen: Int): Int {
        var s = sum(src, 0, src.size) + sum(dst, 0, dst.size) + UDP + udpLen
        s += sum(p, off, udpLen)
        val r = finish(s)
        return if (r == 0) 0xFFFF else r
    }

    internal fun sum(data: ByteArray, off: Int, len: Int): Long {
        var s = 0L
        var i = off
        val end = off + len
        while (i < end) {
            val hi = data[i].toInt() and 0xFF
            val lo = if (i + 1 < end) data[i + 1].toInt() and 0xFF else 0
            s += (hi shl 8) or lo
            i += 2
        }
        return s
    }

    internal fun finish(sum: Long): Int {
        var s = sum
        while (s shr 16 != 0L) s = (s and 0xFFFF) + (s shr 16)
        return s.inv().toInt() and 0xFFFF
    }

    internal fun u16(b: ByteArray, off: Int) = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    internal fun put16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v shr 8).toByte()
        b[off + 1] = v.toByte()
    }
}
