package com.safesailor.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.safesailor.vpn.FilterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estado persistente do app: configurações, listas de sites e estatísticas. */
object Repo {
    /** Depois de digitar o PIN, ações comuns ficam liberadas por este tempo. */
    const val UNLOCK_WINDOW_MS = 2 * 60 * 1000L
    private const val MAX_PIN_ATTEMPTS = 5
    private const val PIN_COOLDOWN_MS = 30_000L

    private lateinit var prefs: SharedPreferences
    @Volatile private var initialized = false

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _blocked = MutableStateFlow<List<SiteEntry>>(emptyList())
    val blocked: StateFlow<List<SiteEntry>> = _blocked.asStateFlow()

    private val _allowed = MutableStateFlow<List<SiteEntry>>(emptyList())
    val allowed: StateFlow<List<SiteEntry>> = _allowed.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    @Volatile private var blockedSet: Set<String> = emptySet()
    @Volatile private var allowedSet: Set<String> = emptySet()
    @Volatile private var unlockedUntil = 0L
    @Volatile private var keepUnlockedOnLeave = false
    private var failedPinAttempts = 0
    @Volatile private var pinBlockedUntil = 0L
    private var lastStatsSave = 0L

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences("safesailor", Context.MODE_PRIVATE)
            _settings.value = loadSettings()
            setBlocked(readSites("blocked"), persist = false)
            setAllowed(readSites("allowed"), persist = false)
            _stats.value = Stats(
                day = prefs.getString("stats_day", "") ?: "",
                blockedToday = prefs.getLong("stats_today", 0),
                blockedTotal = prefs.getLong("stats_total", 0),
                queriesTotal = prefs.getLong("stats_queries", 0),
            ).rollDay()
            initialized = true
        }
    }

    // ---------- Configurações ----------

    fun update(block: (Settings) -> Settings) {
        synchronized(this) {
            val new = block(_settings.value)
            _settings.value = new
            saveSettings(new)
        }
    }

    fun setProtectionEnabled(enabled: Boolean) = update { it.copy(protectionEnabled = enabled) }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            protectionEnabled = prefs.getBoolean("protection", d.protectionEnabled),
            themeMode = enumOr(prefs.getString("theme", null), d.themeMode),
            dynamicColor = prefs.getBoolean("dynamic_color", d.dynamicColor),
            adultFilter = prefs.getBoolean("adult", d.adultFilter),
            keywordFilter = prefs.getBoolean("keywords", d.keywordFilter),
            safeSearch = prefs.getBoolean("safesearch", d.safeSearch),
            youtubeRestricted = prefs.getBoolean("youtube", d.youtubeRestricted),
            blockBypass = prefs.getBoolean("bypass", d.blockBypass),
            dnsProvider = enumOr(prefs.getString("dns", null), d.dnsProvider),
            startOnBoot = prefs.getBoolean("boot", d.startOnBoot),
            logActivity = prefs.getBoolean("log", d.logActivity),
            logAllowed = prefs.getBoolean("log_allowed", d.logAllowed),
            pinHash = prefs.getString("pin_hash", null),
            pinSalt = prefs.getString("pin_salt", null),
            adultListUpdatedAt = prefs.getLong("adult_updated", 0L),
            onboardingDone = prefs.getBoolean("onboarding", false),
            alwaysOnTipDismissed = prefs.getBoolean("always_on_tip", false),
        )
    }

    private fun saveSettings(s: Settings) {
        prefs.edit()
            .putBoolean("protection", s.protectionEnabled)
            .putString("theme", s.themeMode.name)
            .putBoolean("dynamic_color", s.dynamicColor)
            .putBoolean("adult", s.adultFilter)
            .putBoolean("keywords", s.keywordFilter)
            .putBoolean("safesearch", s.safeSearch)
            .putBoolean("youtube", s.youtubeRestricted)
            .putBoolean("bypass", s.blockBypass)
            .putString("dns", s.dnsProvider.name)
            .putBoolean("boot", s.startOnBoot)
            .putBoolean("log", s.logActivity)
            .putBoolean("log_allowed", s.logAllowed)
            .putString("pin_hash", s.pinHash)
            .putString("pin_salt", s.pinSalt)
            .putLong("adult_updated", s.adultListUpdatedAt)
            .putBoolean("onboarding", s.onboardingDone)
            .putBoolean("always_on_tip", s.alwaysOnTipDismissed)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    // ---------- PIN ----------

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        update { it.copy(pinHash = hash(saltStr, pin), pinSalt = saltStr) }
        // Não libera nada: o PIN passa a valer imediatamente.
        lock()
    }

    fun clearPin() {
        update { it.copy(pinHash = null, pinSalt = null) }
        lock()
    }

    /** Verifica o PIN. Após [MAX_PIN_ATTEMPTS] erros seguidos, recusa tudo por 30 s. */
    fun checkPin(pin: String): Boolean = synchronized(this) {
        val s = _settings.value
        val h = s.pinHash ?: return true
        if (pinCooldownSeconds() > 0) return false
        val ok = MessageDigest.isEqual(h.toByteArray(), hash(s.pinSalt ?: "", pin).toByteArray())
        if (ok) {
            failedPinAttempts = 0
        } else if (++failedPinAttempts >= MAX_PIN_ATTEMPTS) {
            failedPinAttempts = 0
            pinBlockedUntil = System.currentTimeMillis() + PIN_COOLDOWN_MS
        }
        ok
    }

    fun pinCooldownSeconds(): Int =
        ((pinBlockedUntil - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toInt().let { if (it > 0) it + 1 else 0 }

    /** Liberado para ações comuns (sem PIN definido, ou PIN digitado há pouco). */
    fun isUnlocked() = !_settings.value.hasPin || System.currentTimeMillis() < unlockedUntil

    fun unlock() {
        unlockedUntil = System.currentTimeMillis() + UNLOCK_WINDOW_MS
    }

    fun lock() {
        unlockedUntil = 0L
        keepUnlockedOnLeave = false
    }

    /** Mantém a liberação ao sair do app para abrir uma tela do sistema pedida pelo usuário. */
    fun allowLeavingUnlocked() {
        keepUnlockedOnLeave = true
    }

    /** Chamado quando o app sai da tela: encerra a liberação, salvo se foi pedido acima. */
    fun onAppLeft() {
        if (keepUnlockedOnLeave) keepUnlockedOnLeave = false else unlockedUntil = 0L
    }

    private fun hash(salt: String, pin: String): String {
        var bytes = (salt + pin).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        repeat(10_000) { bytes = md.digest(bytes) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---------- Listas ----------

    fun filterConfig(adultDomains: Set<String>): FilterConfig {
        val s = _settings.value
        return FilterConfig(
            userBlocked = blockedSet,
            allowed = allowedSet,
            adultFilter = s.adultFilter,
            adultDomains = adultDomains,
            keywordFilter = s.keywordFilter,
            safeSearch = s.safeSearch,
            youtubeRestricted = s.youtubeRestricted,
            blockBypass = s.blockBypass,
        )
    }

    /** Adiciona domínios à lista de bloqueio. Retorna quantos eram novos. */
    fun addBlocked(domains: Collection<String>): Int = synchronized(this) {
        val current = _blocked.value
        val existing = current.associateBy { it.domain }
        var added = 0
        val updated = current.toMutableList()
        for (d in domains) {
            val e = existing[d]
            if (e == null) {
                updated.add(0, SiteEntry(d)); added++
            } else if (!e.enabled) {
                updated[updated.indexOf(e)] = e.copy(enabled = true); added++
            }
        }
        setBlocked(updated)
        val set = domains.toSet()
        if (_allowed.value.any { it.domain in set }) setAllowed(_allowed.value.filterNot { it.domain in set })
        added
    }

    fun removeBlocked(domain: String) = synchronized(this) { setBlocked(_blocked.value.filterNot { it.domain == domain }) }

    fun setBlockedEnabled(domain: String, enabled: Boolean) = synchronized(this) {
        setBlocked(_blocked.value.map { if (it.domain == domain) it.copy(enabled = enabled) else it })
    }

    fun clearBlocked() = synchronized(this) { setBlocked(emptyList()) }

    fun addAllowed(domains: Collection<String>): Int = synchronized(this) {
        val existing = _allowed.value.map { it.domain }.toSet()
        val new = domains.filter { it !in existing }.map { SiteEntry(it) }
        setAllowed(new + _allowed.value)
        val set = domains.toSet()
        if (_blocked.value.any { it.domain in set }) setBlocked(_blocked.value.filterNot { it.domain in set })
        new.size
    }

    fun removeAllowed(domain: String) = synchronized(this) { setAllowed(_allowed.value.filterNot { it.domain == domain }) }

    fun clearAllowed() = synchronized(this) { setAllowed(emptyList()) }

    private fun setBlocked(list: List<SiteEntry>, persist: Boolean = true) {
        _blocked.value = list
        blockedSet = list.filter { it.enabled }.map { it.domain }.toHashSet()
        if (persist) writeSites("blocked", list)
    }

    private fun setAllowed(list: List<SiteEntry>, persist: Boolean = true) {
        _allowed.value = list
        allowedSet = list.map { it.domain }.toHashSet()
        if (persist) writeSites("allowed", list)
    }

    private fun readSites(key: String): List<SiteEntry> = try {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SiteEntry(o.getString("d"), o.optBoolean("e", true), o.optLong("t", 0L))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeSites(key: String, list: List<SiteEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("d", it.domain).put("e", it.enabled).put("t", it.addedAt)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    // ---------- Estatísticas ----------

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun Stats.rollDay(): Stats {
        val t = today()
        return if (day == t) this else copy(day = t, blockedToday = 0)
    }

    fun recordQuery(blocked: Boolean) = synchronized(this) {
        var s = _stats.value.rollDay()
        s = s.copy(
            queriesTotal = s.queriesTotal + 1,
            blockedToday = s.blockedToday + if (blocked) 1 else 0,
            blockedTotal = s.blockedTotal + if (blocked) 1 else 0,
        )
        _stats.value = s
        val now = System.currentTimeMillis()
        if (now - lastStatsSave > 10_000) {
            lastStatsSave = now
            saveStats()
        }
    }

    fun saveStats() {
        if (!initialized) return
        val s = _stats.value
        prefs.edit()
            .putString("stats_day", s.day)
            .putLong("stats_today", s.blockedToday)
            .putLong("stats_total", s.blockedTotal)
            .putLong("stats_queries", s.queriesTotal)
            .apply()
    }

    fun resetStats() = synchronized(this) {
        _stats.value = Stats(day = today())
        saveStats()
    }
}
