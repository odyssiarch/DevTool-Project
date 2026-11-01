package com.jartool.app.core

import android.content.Context
import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.util.IndentingWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.StringWriter
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** Semua class dari semua dex dalam satu JAR (kunci = descriptor, mis. Lcom/foo/Bar;). */
class DexIndex(val classes: Map<String, ClassDef>)

enum class DiffStatus { CHANGED, ADDED, REMOVED }
enum class LineTag { SAME, DEL, ADD }

class MethodDiff(val label: String, val status: DiffStatus, val oldText: String, val newText: String)
class DiffLine(val tag: LineTag, val text: String)

object ClassDiffEngine {
    class Diff(val changed: List<String>, val added: List<String>, val removed: List<String>)

    const val HEADER = "(kepala class, field, anotasi)"

    fun pretty(desc: String) = desc.removePrefix("L").removeSuffix(";").replace('/', '.')
    fun toDesc(name: String) = "L" + name.replace('.', '/') + ";"

    private fun newOpts() = BaksmaliOptions().also {
        it.apiLevel = 35
        it.debugInfo = false // nomor baris tidak ikut dibandingkan
    }

    private fun smali(cd: ClassDef, opts: BaksmaliOptions): String {
        val sw = StringWriter()
        val w = IndentingWriter(sw)
        ClassDefinition(opts, cd).writeTo(w)
        w.flush()
        return sw.toString()
    }

    private fun hash(s: String): Long {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        var h = 0L
        for (i in 0 until 8) h = (h shl 8) or (d[i].toLong() and 0xFF)
        return h
    }

    /** Baca semua dex (termasuk kontainer 041) dari proyek. */
    suspend fun loadIndex(ctx: Context, project: String): DexIndex = withContext(Dispatchers.IO) {
        val map = LinkedHashMap<String, ClassDef>()
        for (f in JarAnalyzer.dexFiles(ctx, project)) {
            val bytes = f.readBytes()
            val version = String(bytes, 4, 3, Charsets.US_ASCII)
            val api = SmaliEngine.apiFor(version)
            for (s in SmaliEngine.logicalStarts(bytes)) {
                val dex = DexBackedDexFile(Opcodes.forApi(api), SmaliEngine.dexView(bytes, s, version))
                for (c in dex.classes) map.putIfAbsent(c.type, c)
            }
        }
        DexIndex(map)
    }

    /** Bandingkan per class lewat hash teks smali. Hasil memakai nama bertitik (com.foo.Bar). */
    suspend fun diff(oldI: DexIndex, newI: DexIndex, onProgress: (Int, Int) -> Unit): Diff =
        withContext(Dispatchers.Default) {
            val removed = oldI.classes.keys.filter { it !in newI.classes }.map { pretty(it) }.sorted()
            val added = newI.classes.keys.filter { it !in oldI.classes }.map { pretty(it) }.sorted()
            val common = oldI.classes.keys.filter { it in newI.classes }.toTypedArray()
            val total = common.size
            val next = AtomicInteger(0)
            val done = AtomicInteger(0)
            val changed = ConcurrentLinkedQueue<String>()
            val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            coroutineScope {
                (1..workers).map {
                    async {
                        val o = newOpts()
                        while (true) {
                            ensureActive()
                            val i = next.getAndIncrement()
                            if (i >= total) break
                            val key = common[i]
                            val a = hash(smali(oldI.classes.getValue(key), o))
                            val b = hash(smali(newI.classes.getValue(key), o))
                            if (a != b) changed.add(pretty(key))
                            val n = done.incrementAndGet()
                            if (n % 200 == 0 || n == total) onProgress(n, total)
                        }
                    }
                }.awaitAll()
            }
            Diff(changed.toList().sorted(), added, removed)
        }

    // ---------- tingkat method ----------

    private class Parts(val methods: LinkedHashMap<String, Pair<String, String>>, val rest: String)

    private fun split(text: String): Parts {
        val methods = LinkedHashMap<String, Pair<String, String>>() // kunci -> (label, isi)
        val rest = StringBuilder()
        var cur: StringBuilder? = null
        var key = ""
        var label = ""
        for (line in text.lineSequence()) {
            val c = cur
            if (c == null) {
                if (line.startsWith(".method ")) {
                    label = line.removePrefix(".method ").trim()
                    key = label.substringAfterLast(' ')
                    cur = StringBuilder().append(line).append('\n')
                } else {
                    rest.append(line).append('\n')
                }
            } else {
                c.append(line).append('\n')
                if (line == ".end method") {
                    methods[key] = label to c.toString().trimEnd()
                    cur = null
                }
            }
        }
        return Parts(methods, rest.toString().trimEnd())
    }

    /** Daftar method yang beda untuk satu class (nama bertitik). */
    fun methodDiff(oldI: DexIndex, newI: DexIndex, name: String): List<MethodDiff> {
        val desc = toDesc(name)
        val o = newOpts()
        val a = split(oldI.classes[desc]?.let { smali(it, o) } ?: "")
        val b = split(newI.classes[desc]?.let { smali(it, o) } ?: "")
        val out = ArrayList<MethodDiff>()
        if (a.rest != b.rest) {
            val st = if (a.rest.isEmpty()) DiffStatus.ADDED else if (b.rest.isEmpty()) DiffStatus.REMOVED else DiffStatus.CHANGED
            out += MethodDiff(HEADER, st, a.rest, b.rest)
        }
        val changed = ArrayList<MethodDiff>()
        val added = ArrayList<MethodDiff>()
        val removed = ArrayList<MethodDiff>()
        for ((k, nv) in b.methods) {
            val ov = a.methods[k]
            if (ov == null) added += MethodDiff(nv.first, DiffStatus.ADDED, "", nv.second)
            else if (ov.second != nv.second) changed += MethodDiff(nv.first, DiffStatus.CHANGED, ov.second, nv.second)
        }
        for ((k, ov) in a.methods) {
            if (k !in b.methods) removed += MethodDiff(ov.first, DiffStatus.REMOVED, ov.second, "")
        }
        out += changed.sortedBy { it.label }
        out += added.sortedBy { it.label }
        out += removed.sortedBy { it.label }
        return out
    }

    /** Diff per baris (LCS). Kalau terlalu besar, dianggap semua baris lama dihapus dan baru ditambah. */
    fun lineDiff(a: List<String>, b: List<String>): List<DiffLine> {
        var s = 0
        while (s < a.size && s < b.size && a[s] == b[s]) s++
        var ea = a.size
        var eb = b.size
        while (ea > s && eb > s && a[ea - 1] == b[eb - 1]) { ea--; eb-- }
        val out = ArrayList<DiffLine>()
        for (i in 0 until s) out += DiffLine(LineTag.SAME, a[i])
        val n = ea - s
        val m = eb - s
        if (n.toLong() * m > 4_000_000L) {
            for (i in s until ea) out += DiffLine(LineTag.DEL, a[i])
            for (j in s until eb) out += DiffLine(LineTag.ADD, b[j])
        } else {
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[s + i] == b[s + j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
            var i = 0
            var j = 0
            while (i < n && j < m) {
                if (a[s + i] == b[s + j]) { out += DiffLine(LineTag.SAME, a[s + i]); i++; j++ }
                else if (dp[i + 1][j] >= dp[i][j + 1]) { out += DiffLine(LineTag.DEL, a[s + i]); i++ }
                else { out += DiffLine(LineTag.ADD, b[s + j]); j++ }
            }
            while (i < n) { out += DiffLine(LineTag.DEL, a[s + i]); i++ }
            while (j < m) { out += DiffLine(LineTag.ADD, b[s + j]); j++ }
        }
        for (i in ea until a.size) out += DiffLine(LineTag.SAME, a[i])
        return out
    }
}

/** Penyimpan sementara hasil perbandingan supaya layar class/method tidak menghitung ulang. */
object DiffSession {
    var oldName: String = ""
    var newName: String = ""
    var oldIndex: DexIndex? = null
    var newIndex: DexIndex? = null
    var result: ClassDiffEngine.Diff? = null
    private val cache = HashMap<String, List<MethodDiff>>()

    @Synchronized
    fun set(oldName: String, newName: String, o: DexIndex, n: DexIndex, r: ClassDiffEngine.Diff) {
        this.oldName = oldName
        this.newName = newName
        oldIndex = o
        newIndex = n
        result = r
        cache.clear()
    }

    @Synchronized
    fun methods(cls: String): List<MethodDiff> {
        cache[cls]?.let { return it }
        val o = oldIndex ?: return emptyList()
        val n = newIndex ?: return emptyList()
        return ClassDiffEngine.methodDiff(o, n, cls).also { cache[cls] = it }
    }
}
