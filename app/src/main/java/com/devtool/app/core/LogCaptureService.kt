package com.devtool.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/**
 * Menangkap logcat (semua buffer) dan dmesg lewat root di latar belakang,
 * jadi tetap hidup saat layar dikunci atau app ditutup. Hasil ditulis ke berkas terus-menerus.
 */
class LogCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val procs = mutableListOf<Process>()
    private var writer: BufferedWriter? = null
    private val total = AtomicLong(0)
    private val matched = AtomicLong(0)
    private val tail = ArrayDeque<String>()
    private var regex: Regex? = null

    @Volatile private var note = ""
    @Volatile private var filePath: String? = null
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish()
            ACTION_START -> begin(intent)
        }
        return START_NOT_STICKY
    }

    private fun begin(i: Intent) {
        startAsForeground("Menyiapkan...")
        if (LogCapture.state.value.running) return
        stopping = false

        val label = i.getStringExtra(EXTRA_LABEL) ?: "log"
        val pattern = i.getStringExtra(EXTRA_PATTERN).orEmpty()
        val allBuf = i.getBooleanExtra(EXTRA_ALL, true)
        val big = i.getBooleanExtra(EXTRA_BIG, true)
        val kernel = i.getBooleanExtra(EXTRA_KERNEL, true)

        regex = if (pattern.isBlank()) {
            null
        } else {
            try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                LogCapture.state.value = LogCapture.State(note = "Regex tidak valid: ${e.message}")
                finish()
                return
            }
        }

        val file = File(LogCapture.logDir(this), "capture_${label}_${LogCapture.stamp()}.txt")
        filePath = file.absolutePath
        writer = BufferedWriter(FileWriter(file, true)).also {
            it.appendLine("# DevTool capture '$label' ${Date()}")
            it.appendLine("# ${Build.MANUFACTURER} ${Build.MODEL} | ${Build.DISPLAY} | SDK ${Build.VERSION.SDK_INT}")
            it.appendLine("# filter: ${pattern.ifBlank { "(semua baris)" }}")
            it.appendLine("# buffer: ${if (allBuf) "all" else "default"} | kernel: $kernel")
        }

        total.set(0)
        matched.set(0)
        synchronized(lock) { tail.clear() }
        note = "Berjalan"
        publish(true)

        scope.launch {
            var n = 0
            while (isActive) {
                delay(500)
                synchronized(lock) { writer?.flush() }
                publish(true)
                if (++n % 10 == 0) updateNotification()
            }
        }
        scope.launch {
            if (big) LogCapture.runRoot("logcat -b all -G 16M", 5000)
            val cmd = (if (allBuf) "logcat -b all" else "logcat") + " -v threadtime -v usec"
            pump("", cmd)
        }
        if (kernel) scope.launch { pump("[K] ", "dmesg -w") }
    }

    private fun pump(prefix: String, cmd: String) {
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            synchronized(procs) { procs.add(p) }
            BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                var line = r.readLine()
                while (line != null) {
                    handle(prefix, line)
                    line = r.readLine()
                }
            }
        } catch (e: Exception) {
            if (!stopping) note = "Gagal ${prefix.trim()} ${e.message}. Pastikan root diizinkan."
        }
        if (!stopping && total.get() == 0L) {
            note = "Tidak ada keluaran. Root ditolak, atau perintah tidak didukung?"
        }
    }

    private fun handle(prefix: String, line: String) {
        total.incrementAndGet()
        val r = regex
        if (r != null && !r.containsMatchIn(line)) return
        matched.incrementAndGet()
        val out = prefix + line
        synchronized(lock) {
            writer?.apply {
                write(out)
                newLine()
            }
            tail.addLast(out)
            if (tail.size > 200) tail.removeFirst()
        }
    }

    private fun publish(running: Boolean) {
        val snap = synchronized(lock) { tail.toList() }
        LogCapture.state.value = LogCapture.State(running, total.get(), matched.get(), filePath, snap, note)
    }

    private fun finish() {
        stopping = true
        val wasRunning = LogCapture.state.value.running
        scope.coroutineContext.cancelChildren()
        synchronized(procs) {
            procs.forEach {
                try {
                    it.destroy()
                } catch (_: Exception) {
                }
            }
            procs.clear()
        }
        Thread {
            try {
                Runtime.getRuntime().exec(
                    arrayOf(
                        "su", "-c",
                        "pkill -f '[l]ogcat -b all -v threadtime -v usec'; " +
                            "pkill -f '[l]ogcat -v threadtime -v usec'; " +
                            "pkill -f '[d]mesg -w'",
                    ),
                ).waitFor()
            } catch (_: Exception) {
            }
        }.start()
        synchronized(lock) {
            try {
                writer?.flush()
                writer?.close()
            } catch (_: Exception) {
            }
            writer = null
        }
        if (wasRunning) {
            note = "Selesai"
            publish(false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        if (LogCapture.state.value.running) {
            LogCapture.state.value = LogCapture.state.value.copy(running = false, note = "Layanan dihentikan sistem")
        }
        super.onDestroy()
    }

    // ---------- notifikasi ----------
    private fun startAsForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tangkap log", NotificationManager.IMPORTANCE_LOW),
        )
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, LogCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("DevTool menangkap log")
            .setContentText(text)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Berhenti", stop).build())
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification("${matched.get()} baris cocok dari ${total.get()}"))
    }

    companion object {
        const val ACTION_START = "com.devtool.app.capture.START"
        const val ACTION_STOP = "com.devtool.app.capture.STOP"
        const val EXTRA_LABEL = "label"
        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_ALL = "all_buffers"
        const val EXTRA_BIG = "big_buffer"
        const val EXTRA_KERNEL = "kernel"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 4101

        fun start(ctx: Context, label: String, pattern: String, allBuffers: Boolean, bigBuffer: Boolean, kernel: Boolean) {
            val i = Intent(ctx, LogCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_PATTERN, pattern)
                .putExtra(EXTRA_ALL, allBuffers)
                .putExtra(EXTRA_BIG, bigBuffer)
                .putExtra(EXTRA_KERNEL, kernel)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, LogCaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
