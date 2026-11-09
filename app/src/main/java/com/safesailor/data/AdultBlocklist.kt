package com.safesailor.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lista de domínios adultos. Vem embutida no app (StevenBlack/hosts, extensão "porn",
 * licença MIT) e pode ser atualizada pela internet nas configurações.
 */
object AdultBlocklist {
    private const val TAG = "AdultBlocklist"
    const val SOURCE_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
    private const val ASSET = "adult_domains.txt"
    private const val FILE = "adult_domains_updated.txt"

    @Volatile var domains: Set<String> = emptySet()
        private set

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val ctx = context.applicationContext
            val updated = File(ctx.filesDir, FILE)
            val set = try {
                if (updated.length() > 0) updated.inputStream().use(::parse) else ctx.assets.open(ASSET).use(::parse)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao carregar lista", e)
                try { ctx.assets.open(ASSET).use(::parse) } catch (_: Exception) { emptySet() }
            }
            domains = set
            _count.value = set.size
            loaded = true
        }
    }

    /** Baixa a versão mais recente. Retorna o número de domínios. */
    suspend fun update(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(SOURCE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "SafeSailor-Android")
            }
            try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                val set = conn.inputStream.use(::parse)
                if (set.size < 1000) error("Lista recebida parece incompleta")
                val dir = context.applicationContext.filesDir
                val tmp = File(dir, "$FILE.tmp")
                tmp.bufferedWriter().use { w -> set.forEach { w.write(it); w.write("\n") } }
                if (!tmp.renameTo(File(dir, FILE))) error("Não foi possível salvar a lista")
                domains = set
                _count.value = set.size
                loaded = true
                Repo.update { it.copy(adultListUpdatedAt = System.currentTimeMillis()) }
                set.size
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parse(input: InputStream): Set<String> {
        val set = HashSet<String>(100_000)
        input.bufferedReader().use { r: BufferedReader ->
            r.forEachLine { raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachLine
                val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
                val d = (if (parts.size >= 2 && parts[0].all { it.isDigit() || it == '.' || it == ':' }) parts[1] else parts[0])
                    .lowercase().trimEnd('.')
                if (d.contains('.') && d != "0.0.0.0" && d != "localhost") set.add(d)
            }
        }
        return set
    }
}
