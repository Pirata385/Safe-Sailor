package com.safesailor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Registro em memória das consultas recentes (não sai do aparelho). */
object ActivityLog {
    private const val MAX = 500
    private val ids = AtomicLong()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(domain: String, type: LogType) = synchronized(this) {
        val now = System.currentTimeMillis()
        val current = _entries.value
        // Navegadores pedem A, AAAA e HTTPS para o mesmo site: evita linhas repetidas.
        if (current.take(6).any { it.domain == domain && it.type == type && now - it.time < 3_000 }) return
        val entry = LogEntry(ids.incrementAndGet(), domain, now, type)
        _entries.value = (listOf(entry) + current).take(MAX)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
