package com.safesailor.util

import java.net.IDN

object DomainUtils {
    private val labelRegex = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
    private val separators = Regex("[\\s,;]+")
    private val secondLevel = setOf("com", "net", "org", "gov", "edu", "co", "ac", "mil", "art", "blog", "bet", "app", "tv")

    /**
     * Converte o que o usuário digitou (URL, domínio, com ou sem "www")
     * em um domínio normalizado, ou null se for inválido.
     * Ex.: "https://www.Instagram.com/explore" -> "instagram.com"
     */
    fun normalize(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')
        s = s.substringBefore(':')
        s = s.trim('.').removePrefix("*.")
        if (s.startsWith("www.") && s.count { it == '.' } >= 2) s = s.removePrefix("www.")
        s = try {
            IDN.toASCII(s, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (e: Exception) {
            return null
        }
        if (s.length > 253 || !s.contains('.')) return null
        val labels = s.split('.')
        if (!labels.all { labelRegex.matches(it) }) return null
        if (labels.last().all { it.isDigit() }) return null
        return s
    }

    /** Aceita vários endereços separados por espaço, vírgula, ponto e vírgula ou quebra de linha. */
    fun parseMany(text: String): List<String> =
        text.split(separators).mapNotNull(::normalize).distinct()

    /** Retorna o primeiro sufixo de [domain] (incluindo ele mesmo) presente em [set]. */
    fun findMatch(domain: String, set: Set<String>): String? {
        if (set.isEmpty()) return null
        var d = domain
        while (true) {
            if (d in set) return d
            val i = d.indexOf('.')
            if (i < 0) return null
            d = d.substring(i + 1)
        }
    }

    /** Aproximação do domínio "raiz" (ex.: scontent.cdninstagram.com -> cdninstagram.com, a.b.com.br -> b.com.br). */
    fun rootDomain(domain: String): String {
        val parts = domain.split('.')
        if (parts.size <= 2) return domain
        val tld = parts.last()
        val sld = parts[parts.size - 2]
        val take = if (tld.length == 2 && sld in secondLevel) 3 else 2
        return parts.takeLast(take).joinToString(".")
    }
}
