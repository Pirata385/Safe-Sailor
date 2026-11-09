package com.safesailor.vpn

import com.safesailor.vpn.IpPacket.put16
import com.safesailor.vpn.IpPacket.u16
import java.io.ByteArrayOutputStream

data class DnsQuestion(val name: String, val type: Int, val qclass: Int, val end: Int)

object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val RCODE_NXDOMAIN = 3

    fun parseQuestion(m: ByteArray): DnsQuestion? {
        if (m.size < 17) return null
        if ((m[2].toInt() and 0x80) != 0) return null // é resposta, não consulta
        if (u16(m, 4) < 1) return null
        var pos = 12
        val sb = StringBuilder()
        while (true) {
            if (pos >= m.size) return null
            val l = m[pos].toInt() and 0xFF
            if (l == 0) {
                pos++
                break
            }
            if (l and 0xC0 != 0) return null
            if (pos + 1 + l > m.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in pos + 1..pos + l) sb.append((m[i].toInt() and 0xFF).toChar().lowercaseChar())
            pos += 1 + l
            if (sb.length > 255) return null
        }
        if (pos + 4 > m.size) return null
        return DnsQuestion(sb.toString(), u16(m, pos), u16(m, pos + 2), pos + 4)
    }

    private fun header(query: ByteArray, rcode: Int, answers: Int): ByteArray {
        val h = ByteArray(12)
        h[0] = query[0]
        h[1] = query[1]
        h[2] = (0x80 or (query[2].toInt() and 0x79)).toByte() // QR + opcode + RD
        h[3] = (0x80 or rcode).toByte() // RA + rcode
        put16(h, 4, 1)
        put16(h, 6, answers)
        return h
    }

    private fun question(query: ByteArray, q: DnsQuestion) = query.copyOfRange(12, q.end)

    /** Resposta "domínio não existe" — usada para bloquear. */
    fun nxdomain(query: ByteArray, q: DnsQuestion): ByteArray = header(query, RCODE_NXDOMAIN, 0) + question(query, q)

    /** Resposta vazia (sem registros) — usada para tipos que não sabemos reescrever. */
    fun empty(query: ByteArray, q: DnsQuestion): ByteArray = header(query, 0, 0) + question(query, q)

    fun addressResponse(query: ByteArray, q: DnsQuestion, rdatas: List<ByteArray>, ttl: Int = 300): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(header(query, 0, rdatas.size))
        out.write(question(query, q))
        for (rd in rdatas) {
            val r = ByteArray(12)
            r[0] = 0xC0.toByte()
            r[1] = 0x0C
            put16(r, 2, q.type)
            put16(r, 4, 1)
            put16(r, 6, ttl ushr 16)
            put16(r, 8, ttl and 0xFFFF)
            put16(r, 10, rd.size)
            out.write(r)
            out.write(rd)
        }
        return out.toByteArray()
    }

    fun buildQuery(id: Int, name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val h = ByteArray(12)
        put16(h, 0, id)
        h[2] = 0x01 // RD
        put16(h, 4, 1)
        out.write(h)
        for (label in name.split('.')) {
            if (label.isEmpty()) continue
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        val t = ByteArray(4)
        put16(t, 0, type)
        put16(t, 2, 1)
        out.write(t)
        return out.toByteArray()
    }

    /** Extrai os dados (endereços) dos registros de resposta do tipo [type]. */
    fun extractAddresses(resp: ByteArray, type: Int): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        try {
            if (resp.size < 12) return result
            val qd = u16(resp, 4)
            val an = u16(resp, 6)
            var pos = 12
            repeat(qd) { pos = skipName(resp, pos) + 4 }
            repeat(an) {
                pos = skipName(resp, pos)
                if (pos + 10 > resp.size) return result
                val t = u16(resp, pos)
                val rdLen = u16(resp, pos + 8)
                val start = pos + 10
                if (start + rdLen > resp.size) return result
                if (t == type) result.add(resp.copyOfRange(start, start + rdLen))
                pos = start + rdLen
            }
        } catch (e: IndexOutOfBoundsException) {
            // resposta malformada: devolve o que já foi lido
        }
        return result
    }

    private fun skipName(m: ByteArray, start: Int): Int {
        var pos = start
        while (true) {
            val l = m[pos].toInt() and 0xFF
            when {
                l == 0 -> return pos + 1
                l and 0xC0 == 0xC0 -> return pos + 2
                else -> pos += 1 + l
            }
        }
    }
}
