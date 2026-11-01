package com.jartool.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Satu class yang isinya berbeda antara versi lama dan versi baru. */
class ClassChange(
    val key: String,
    val added: Int,
    val removed: Int,
    val changed: Int,
    val other: Boolean,
) {
    val simpleName: String get() = key.removeSuffix(".smali").substringAfterLast('/')
    val dotted: String get() = key.removeSuffix(".smali").replace('/', '.')
}

/** Satu method (atau bagian lain di luar method) yang berbeda. kind: BARU, DIHAPUS, BERUBAH. */
class MethodChange(
    val name: String,
    val header: String,
    val kind: String,
    val oldText: String,
    val newText: String,
)

class DeepResult(
    val oldFiles: Map<String, File>,
    val newFiles: Map<String, File>,
    val changes: List<ClassChange>,
    val total: Int,
)

/** kind: ' ' sama, '+' baru, '-' hilang, '~' penanda baris yang dilewati. */
class DiffLine(val kind: Char, val text: String)

object SmaliDiff {

    private class Parsed(val methods: LinkedHashMap<String, String>, val headers: HashMap<String, String>, val rest: String)

    private fun parse(text: String): Parsed {
        val methods = LinkedHashMap<String, String>()
        val headers = HashMap<String, String>()
        val rest = StringBuilder()
        val lines = text.split("\n")
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith(".method ")) {
                val head = l.trim()
                val key = head.substringAfterLast(' ')
                val sb = StringBuilder()
                var j = i
                while (j < lines.size) {
                    sb.append(lines[j]).append('\n')
                    if (lines[j].trim() == ".end method") break
                    j++
                }
                methods[key] = sb.toString()
                headers[key] = head
                i = j + 1
            } else {
                // baris kosong di antara method tidak dihitung, supaya tambah/hapus method tidak dianggap bagian lain berubah
                if (l.isNotBlank()) rest.append(l).append('\n')
                i++
            }
        }
        return Parsed(methods, headers, rest.toString())
    }

    // ---------- bongkar ke folder cache, lalu bandingkan ----------

    private fun smaliFiles(ctx: Context, project: String, label: String, log: (String) -> Unit): Map<String, File> {
        val jar = Workspace.jarOf(ctx, project)
        val base = File(ctx.cacheDir, "cmp")
        val safe = project.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(base, "${safe}_${jar.length()}_${jar.lastModified()}")
        val done = File(dir, ".selesai")
        if (!done.exists()) {
            val lama = Regex("^" + Regex.escape(safe) + "_\\d+_\\d+$")
            base.listFiles()?.filter { lama.matches(it.name) }?.forEach { it.deleteRecursively() }
            dir.mkdirs()
            log("Membongkar versi $label (beberapa menit untuk JAR besar)...")
            val dexDir = File(dir, "_dex")
            val dexes = JarPackager.extractDex(jar, dexDir)
            if (dexes.isEmpty()) throw IllegalStateException("Versi $label tidak berisi classes.dex")
            for (d in dexes) {
                SmaliEngine.disassembleBlocking(d, dir, log)
                d.delete()
                System.gc()
            }
            dexDir.deleteRecursively()
            done.writeText("ok")
        } else {
            log("Versi $label sudah pernah dibongkar, memakai hasil sebelumnya")
        }
        val map = HashMap<String, File>()
        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith("_") }?.sortedBy { it.name }?.forEach { d ->
            d.walkTopDown().filter { it.isFile && it.name.endsWith(".smali") }.forEach { f ->
                map[f.relativeTo(d).path] = f
            }
        }
        return map
    }

    suspend fun compare(ctx: Context, oldProject: String, newProject: String, log: (String) -> Unit): DeepResult =
        withContext(Dispatchers.IO) {
            val a = smaliFiles(ctx, oldProject, "lama", log)
            val b = smaliFiles(ctx, newProject, "baru", log)
            val changes = ArrayList<ClassChange>()
            var n = 0
            for ((key, fa) in a) {
                n++
                if (n % 3000 == 0) log("Membandingkan class $n dari ${a.size}...")
                val fb = b[key] ?: continue
                val ta = fa.readText()
                val tb = fb.readText()
                if (ta == tb) continue
                val pa = parse(ta)
                val pb = parse(tb)
                var add = 0
                var rem = 0
                var chg = 0
                for ((k, t) in pb.methods) {
                    val o = pa.methods[k]
                    if (o == null) add++ else if (o != t) chg++
                }
                for (k in pa.methods.keys) if (!pb.methods.containsKey(k)) rem++
                val lain = pa.rest != pb.rest
                if (add + rem + chg == 0 && !lain) continue
                changes.add(ClassChange(key, add, rem, chg, lain))
            }
            changes.sortWith(
                compareByDescending<ClassChange> { it.added + it.removed + it.changed }.thenBy { it.key },
            )
            log("Selesai: ${changes.size} class berubah dari ${a.size}")
            DeepResult(a, b, changes, a.size)
        }

    /** Daftar method yang berbeda di satu class. */
    fun methodsOf(d: DeepResult, key: String): List<MethodChange> {
        val fa = d.oldFiles[key]
        val fb = d.newFiles[key]
        if (fa == null || fb == null) return emptyList()
        val pa = parse(fa.readText())
        val pb = parse(fb.readText())
        val out = ArrayList<MethodChange>()
        for ((k, t) in pb.methods) {
            val o = pa.methods[k]
            val head = pb.headers[k] ?: k
            if (o == null) out.add(MethodChange(k, head, "BARU", "", t))
            else if (o != t) out.add(MethodChange(k, head, "BERUBAH", o, t))
        }
        for ((k, t) in pa.methods) {
            if (!pb.methods.containsKey(k)) out.add(MethodChange(k, pa.headers[k] ?: k, "DIHAPUS", t, ""))
        }
        if (pa.rest != pb.rest) {
            out.add(MethodChange("(bagian di luar method)", "Field, anotasi, dan header class", "BERUBAH", pa.rest, pb.rest))
        }
        return out
    }

    // ---------- selisih per baris ----------

    fun lineDiff(oldText: String, newText: String): List<DiffLine> {
        val a: List<String> = if (oldText.isEmpty()) emptyList() else oldText.trimEnd('\n').split("\n")
        val b: List<String> = if (newText.isEmpty()) emptyList() else newText.trimEnd('\n').split("\n")
        var s = 0
        while (s < a.size && s < b.size && a[s] == b[s]) s++
        var ea = a.size
        var eb = b.size
        while (ea > s && eb > s && a[ea - 1] == b[eb - 1]) {
            ea--
            eb--
        }
        val out = ArrayList<DiffLine>()
        for (i in 0 until s) out.add(DiffLine(' ', a[i]))
        val n = ea - s
        val m = eb - s
        if (n.toLong() * m <= 2_000_000L) {
            val w = m + 1
            val t = IntArray((n + 1) * w)
            for (i in n - 1 downTo 0) {
                for (j in m - 1 downTo 0) {
                    t[i * w + j] = if (a[s + i] == b[s + j]) {
                        t[(i + 1) * w + j + 1] + 1
                    } else {
                        maxOf(t[(i + 1) * w + j], t[i * w + j + 1])
                    }
                }
            }
            var i = 0
            var j = 0
            while (i < n && j < m) {
                if (a[s + i] == b[s + j]) {
                    out.add(DiffLine(' ', a[s + i]))
                    i++
                    j++
                } else if (t[(i + 1) * w + j] >= t[i * w + j + 1]) {
                    out.add(DiffLine('-', a[s + i]))
                    i++
                } else {
                    out.add(DiffLine('+', b[s + j]))
                    j++
                }
            }
            while (i < n) {
                out.add(DiffLine('-', a[s + i]))
                i++
            }
            while (j < m) {
                out.add(DiffLine('+', b[s + j]))
                j++
            }
        } else {
            for (i in 0 until n) out.add(DiffLine('-', a[s + i]))
            for (j in 0 until m) out.add(DiffLine('+', b[s + j]))
        }
        for (i in ea until a.size) out.add(DiffLine(' ', a[i]))
        return out
    }

    /** Sisakan baris yang berubah ditambah [context] baris di sekitarnya. */
    fun compact(lines: List<DiffLine>, context: Int = 2): List<DiffLine> {
        val keep = BooleanArray(lines.size)
        for (i in lines.indices) {
            if (lines[i].kind != ' ') {
                val from = maxOf(0, i - context)
                val to = minOf(lines.size - 1, i + context)
                for (k in from..to) keep[k] = true
            }
        }
        val out = ArrayList<DiffLine>()
        var skipped = 0
        for (i in lines.indices) {
            if (keep[i]) {
                if (skipped > 0) {
                    out.add(DiffLine('~', "... $skipped baris sama ..."))
                    skipped = 0
                }
                out.add(lines[i])
            } else {
                skipped++
            }
        }
        if (skipped > 0) out.add(DiffLine('~', "... $skipped baris sama ..."))
        return out
    }
}
