package com.safesailor.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.safesailor.R
import com.safesailor.SafeSailorApplication
import com.safesailor.data.ActivityLog
import com.safesailor.data.AdultBlocklist
import com.safesailor.data.LogType
import com.safesailor.data.Repo
import com.safesailor.ui.MainActivity
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * VPN local que intercepta apenas as consultas DNS do aparelho. Nenhum outro tráfego
 * passa pelo app: domínios bloqueados recebem "não existe" e os demais são
 * encaminhados ao servidor DNS escolhido.
 */
class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "DnsVpnService"
        const val ACTION_START = "com.safesailor.action.START"
        const val ACTION_STOP = "com.safesailor.action.STOP"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2
        private const val ADDR4 = "10.111.222.1"
        private const val DNS4 = "10.111.222.2"
        private const val ADDR6 = "fd00:5afe:5a11::1"
        private const val DNS6 = "fd00:5afe:5a11::2"

        fun start(context: Context) {
            val intent = Intent(context, DnsVpnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, DnsVpnService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                Log.w(TAG, "stop", e)
            }
        }
    }

    private val lock = Any()
    private var tun: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null
    private var pipeWrite: FileDescriptor? = null
    private var worker: Thread? = null
    private var executor: ThreadPoolExecutor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        Repo.init(this)
        startInForeground()
        // Iniciado pelo sistema (VPN sempre ativa) ou recriado após ser encerrado.
        if (intent == null || intent.action == SERVICE_INTERFACE) Repo.setProtectionEnabled(true)
        if (tun == null && !establish()) {
            Repo.setProtectionEnabled(false)
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // O tipo "specialUse" está declarado no AndroidManifest principal; o lint da edição
    // "completa" analisa o manifesto parcial da edição e acusa um falso positivo.
    @SuppressLint("ForegroundServiceType")
    private fun startInForeground() {
        NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, SafeSailorApplication.CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_stat_boat)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setColor(ContextCompat.getColor(this, R.color.brand_blue))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground falhou", e)
        }
    }

    private fun establish(): Boolean {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(ADDR4, 32)
            .addDnsServer(DNS4)
            .addRoute(DNS4, 32)
            .setMtu(1500)
            .setBlocking(true)
            .setConfigureIntent(
                PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
        try {
            builder.addAddress(ADDR6, 128).addDnsServer(DNS6).addRoute(DNS6, 128)
        } catch (e: Exception) {
            Log.w(TAG, "IPv6 indisponível", e)
        }
        try {
            builder.addDisallowedApplication(packageName) // o próprio app sai direto (atualização de listas)
        } catch (_: PackageManager.NameNotFoundException) {
        }
        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish", e)
            null
        } ?: return false

        val pipe = Os.pipe()
        synchronized(lock) {
            tun = pfd
            output = FileOutputStream(pfd.fileDescriptor)
            pipeWrite = pipe[1]
            executor = ThreadPoolExecutor(
                4, 16, 30, TimeUnit.SECONDS, LinkedBlockingQueue(1024), ThreadPoolExecutor.DiscardOldestPolicy(),
            ).apply { allowCoreThreadTimeOut(true) }
        }
        worker = Thread({ runLoop(pfd.fileDescriptor, pipe[0]) }, "SafeSailor-DNS").apply { start() }
        VpnStatus.set(this, true)
        return true
    }

    private fun runLoop(tunFd: FileDescriptor, pipeRead: FileDescriptor) {
        AdultBlocklist.ensureLoaded(this)
        val buf = ByteArray(32767)
        try {
            while (true) {
                val pollTun = StructPollfd().apply { fd = tunFd; events = OsConstants.POLLIN.toShort() }
                val pollPipe = StructPollfd().apply { fd = pipeRead; events = OsConstants.POLLIN.toShort() }
                try {
                    Os.poll(arrayOf(pollTun, pollPipe), -1)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    throw e
                }
                if (pollPipe.revents.toInt() != 0) break
                val rev = pollTun.revents.toInt()
                if (rev and OsConstants.POLLIN != 0) {
                    val n = Os.read(tunFd, buf, 0, buf.size)
                    if (n <= 0) continue
                    val query = IpPacket.parseDnsQuery(buf, n) ?: continue
                    executor?.execute { handle(query) }
                } else if (rev and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Loop encerrado", e)
        } finally {
            try { Os.close(pipeRead) } catch (_: Exception) {}
        }
    }

    private fun handle(q: DnsQueryPacket) {
        try {
            val question = DnsMessage.parseQuestion(q.dns)
            if (question == null) {
                forward(q.dns)?.let { reply(q, it) }
                return
            }
            val settings = Repo.settings.value
            when (val decision = DnsFilter.decide(question.name, Repo.filterConfig(AdultBlocklist.domains))) {
                is Decision.Block -> {
                    reply(q, DnsMessage.nxdomain(q.dns, question))
                    Repo.recordQuery(blocked = true)
                    if (settings.logActivity) {
                        ActivityLog.add(
                            question.name,
                            when (decision.reason) {
                                BlockReason.USER -> LogType.BLOCKED_USER
                                BlockReason.ADULT_LIST -> LogType.BLOCKED_ADULT
                                BlockReason.KEYWORD -> LogType.BLOCKED_KEYWORD
                                BlockReason.BYPASS -> LogType.BLOCKED_BYPASS
                            },
                        )
                    }
                }
                is Decision.Rewrite -> {
                    val resp = if (question.type == DnsMessage.TYPE_A || question.type == DnsMessage.TYPE_AAAA) {
                        val sub = DnsMessage.buildQuery(Random.nextInt(0, 0xFFFF), decision.target, question.type)
                        val upstream = forward(sub) ?: return
                        DnsMessage.addressResponse(q.dns, question, DnsMessage.extractAddresses(upstream, question.type))
                    } else {
                        DnsMessage.empty(q.dns, question)
                    }
                    reply(q, resp)
                    Repo.recordQuery(blocked = false)
                    if (settings.logActivity) ActivityLog.add(question.name, LogType.SAFESEARCH)
                }
                Decision.Allow -> {
                    val resp = forward(q.dns) ?: return
                    reply(q, resp)
                    Repo.recordQuery(blocked = false)
                    if (settings.logActivity && settings.logAllowed) ActivityLog.add(question.name, LogType.ALLOWED)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "handle", e)
        }
    }

    /** Envia a consulta ao servidor DNS escolhido (com servidor reserva). */
    private fun forward(dns: ByteArray): ByteArray? {
        val provider = Repo.settings.value.dnsProvider
        for (server in listOf(provider.primary, provider.secondary)) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 4000
                    socket.send(DatagramPacket(dns, dns.size, InetAddress.getByName(server), 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    socket.receive(rp)
                    return rb.copyOf(rp.length)
                }
            } catch (e: IOException) {
                Log.d(TAG, "DNS $server falhou: ${e.message}")
            }
        }
        return null
    }

    private fun reply(q: DnsQueryPacket, dns: ByteArray) {
        val packet = IpPacket.buildResponse(q, dns)
        synchronized(lock) {
            try {
                output?.write(packet)
            } catch (e: IOException) {
                Log.d(TAG, "write: ${e.message}")
            }
        }
    }

    private fun shutdown() {
        synchronized(lock) {
            pipeWrite?.let { try { Os.close(it) } catch (_: Exception) {} }
            pipeWrite = null
            executor?.shutdownNow()
            executor = null
            try { tun?.close() } catch (_: Exception) {}
            tun = null
            output = null
        }
        worker = null
        Repo.saveStats()
        VpnStatus.set(this, false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        // Outro app de VPN assumiu ou alguém desconectou nas configurações do sistema.
        val s = Repo.settings.value
        if (s.hasPin && s.protectionEnabled) {
            // Com PIN, isso não conta como desligar: mantém a proteção pedida e avisa.
            notifyInterrupted()
        } else {
            Repo.setProtectionEnabled(false)
        }
        shutdown()
        super.onRevoke()
    }

    private fun notifyInterrupted() {
        val open = PendingIntent.getActivity(
            this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, SafeSailorApplication.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_boat)
            .setContentTitle(getString(R.string.alert_interrupted_title))
            .setContentText(getString(R.string.alert_interrupted_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.alert_interrupted_text)))
            .setColor(ContextCompat.getColor(this, R.color.brand_blue))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(ALERT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão de notificação", e)
        }
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
