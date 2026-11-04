package com.devtool.app.core

import android.content.Context
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JavaClass
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pembungkus jadx. Satu proyek dimuat sekali, lalu class didekompilasi satu per satu
 * saat dibuka supaya JAR besar seperti services.jar tidak didekompilasi seluruhnya.
 */
object Decompiler {
    private var project: String? = null
    private var jadx: JadxDecompiler? = null
    private var byName: Map<String, JavaClass> = emptyMap()

    fun isLoaded(p: String): Boolean = jadx != null && project == p

    fun close() {
        runCatching { jadx?.close() }
        jadx = null
        project = null
        byName = emptyMap()
    }

    /** Jadx butuh stack besar untuk method yang dalam, jadi dijalankan di thread sendiri. */
    private suspend fun <T> onBigStack(block: () -> T): T = suspendCancellableCoroutine { cont ->
        Thread(
            null,
            Runnable {
                try {
                    cont.resume(block())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            },
            "jadx-worker",
            64L * 1024 * 1024,
        ).start()
    }

    suspend fun load(ctx: Context, p: String) {
        if (isLoaded(p)) return
        close()
        val files = JarAnalyzer.dexFiles(ctx, p)
        if (files.isEmpty()) error("JAR ini tidak berisi classes.dex")
        val threads = Prefs.threads(ctx)
        val out = File(ctx.cacheDir, "jadx-out").apply { mkdirs() }

        val (decompiler, map) = onBigStack {
            val args = JadxArgs()
            args.setInputFiles(files)
            args.setOutDir(out)
            args.setThreadsCount(threads)
            args.setShowInconsistentCode(true)
            args.setDeobfuscationOn(false)
            args.setSkipResources(true)
            args.setPluginOptions(HashMap(mapOf("dex-input.verify-checksum" to "no")))
            val j = JadxDecompiler(args)
            j.load()
            j to j.classes.associateBy { it.fullName }
        }
        jadx = decompiler
        byName = map
        project = p
    }

    suspend fun decompile(name: String): String {
        if (jadx == null) error("Dekompiler belum dimuat")
        val top = name.substringBefore('$')
        val cls = byName[top] ?: byName[name.replace('$', '.')] ?: error("jadx tidak menemukan class $top")
        return onBigStack { cls.code }
    }
}
