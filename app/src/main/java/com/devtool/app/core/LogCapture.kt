package com.devtool.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keadaan bersama antara LogCaptureService dan layar Tangkap Log, plus preset dan dump diagnostik. */
object LogCapture {
    class Preset(val id: String, val label: String, val pattern: String)

    val presets = listOf(
        Preset(
            "fp", "Fingerprint / FOD",
            """finger|(?<![a-z])fp[_ :]|fpc_|udfps|(?<![a-z])fod(?![a-z])|biometric|goodix|egis|cdfinger|sehfp|gatekeeper|keymint|keymaster|ultrasonic""",
        ),
        Preset(
            "face", "Face unlock",
            """(?<![a-z])face(?![a-z])|faceservice|faceauth|facehal|facemanager|faceunlock|semface|biometrics\.face|faceapi|face_|(?<![a-z])iris|biometric|gatekeeper|keymint|keymaster""",
        ),
        Preset("avc", "SELinux denial", """avc: *denied|type=1400"""),
        Preset(
            "crash", "Crash / HAL mati",
            """FATAL EXCEPTION|beginning of crash|backtrace:|Fatal signal|has died|binderDied|ANR in|tombstone""",
        ),
        Preset("all", "Semua (tanpa filter)", ""),
    )

    data class State(
        val running: Boolean = false,
        val total: Long = 0,
        val matched: Long = 0,
        val file: String? = null,
        val tail: List<String> = emptyList(),
        val note: String = "",
    )

    val state = MutableStateFlow(State())

    fun logDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "logs").apply { mkdirs() }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Jalankan satu perintah sebagai root, ambil keluarannya (dengan batas waktu). */
    fun runRoot(cmd: String, timeoutMs: Long = 15000): String {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val sb = StringBuffer()
            val t = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { sb.append(it).append('\n') }
                } catch (_: Exception) {
                }
            }
            t.start()
            t.join(timeoutMs)
            if (t.isAlive) {
                p.destroy()
                sb.append("[batas waktu habis]\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "[gagal: ${e.message}]"
        }
    }

    private val diagSteps = listOf(
        "Info build" to "getprop ro.build.display.id; getprop ro.product.model; getprop ro.product.device; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.hardware; getprop ro.board.platform; getprop ro.boot.verifiedbootstate; getenforce",
        "Properti biometrik" to "getprop | grep -i -E 'finger|fp|face|biometric|udfps|fod|ultrasonic|optical'",
        "Service terdaftar" to "service list | grep -i -E 'finger|face|biometric|iris'",
        "HAL terdaftar (lshal)" to "lshal 2>/dev/null | grep -i -E 'finger|face|biometric'",
        "Proses berjalan" to "ps -A | grep -i -E 'finger|face|biometric|gatekeeper|keymaster|keymint|goodix|egis|cdfinger|trusty|qseecom'",
        "Perangkat di /dev" to "ls -l /dev | grep -i -E 'fp|finger|goodix|egis|cdfinger|tee|trusty|qseecom'",
        "Library HAL" to "ls /vendor/lib64/hw /vendor/lib/hw /system/lib64/hw /odm/lib64/hw 2>/dev/null | grep -i -E 'finger|face|biometric|gatekeeper|keymaster|keymint'",
        "Berkas init rc" to "ls /vendor/etc/init /system/etc/init /odm/etc/init 2>/dev/null | grep -i -E 'finger|face|biometric'",
        "VINTF manifest yang menyebut biometrik" to "grep -il -E 'fingerprint|face|biometric' /vendor/etc/vintf/manifest.xml /vendor/etc/vintf/manifest/* /odm/etc/vintf/* /system/etc/vintf/* 2>/dev/null",
        "dumpsys fingerprint" to "dumpsys fingerprint",
        "dumpsys face" to "dumpsys face",
        "dumpsys biometric" to "dumpsys biometric",
        "dmesg: SELinux denial biometrik" to "dmesg | grep -i 'avc: .*denied' | grep -i -E 'finger|face|biometric|hal_' | tail -n 80",
        "dmesg: driver sidik jari / TEE" to "dmesg | grep -i -E 'finger|goodix|egis|cdfinger|fp_|udfps|trusty|qseecom' | tail -n 150",
        "Logcat buffer crash" to "logcat -b crash -d | tail -n 200",
        "Tombstone terbaru" to "ls -lt /data/tombstones 2>/dev/null | head -n 10",
        "Dropbox sistem terbaru" to "ls -lt /data/system/dropbox 2>/dev/null | head -n 15",
    )

    /** Kumpulkan info perangkat yang berguna untuk debug biometrik di ROM port. */
    fun diagnose(ctx: Context, onStep: (String) -> Unit = {}): File {
        val f = File(logDir(ctx), "diagnostik_${stamp()}.txt")
        f.bufferedWriter().use { w ->
            for ((title, cmd) in diagSteps) {
                onStep(title)
                w.appendLine("===== $title =====")
                w.appendLine("$ $cmd")
                val out = runRoot(cmd)
                w.appendLine(if (out.isBlank()) "(kosong)" else out.trimEnd())
                w.appendLine()
            }
        }
        return f
    }
}
