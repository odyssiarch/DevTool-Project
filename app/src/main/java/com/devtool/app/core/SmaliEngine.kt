package com.devtool.app.core

import android.content.Context
import com.android.tools.smali.baksmali.Baksmali
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * Mesin bongkar dan rakit ulang Smali.
 *
 * Tata letak folder kerja (di folder aplikasi, bisa dibuka lewat pengelola berkas):
 *   smali/<proyek>/classes.dex-1/   <- dex logis pertama dalam classes.dex
 *   smali/<proyek>/classes.dex-2/   <- dex logis kedua (kalau classes.dex berupa kontainer 041)
 *   smali/<proyek>/layout.txt       <- catatan: nama berkas | versi | folder-foldernya
 */
object SmaliEngine {

    private fun threadCount() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    /** Versi dex -> API level untuk membaca/menulis. */
    fun apiFor(version: String): Int = when (version) {
        "035" -> 23
        "037" -> 25
        "038" -> 27
        "039" -> 29 // 29 (bukan 28): smali baru mau membaca flag hidden API (blacklist/greylist) dari api 29
        "040" -> 34
        else -> 35
    }

    // ---------- pembaca byte kecil ----------

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun put32(b: ByteArray, o: Int, v: Int) {
        for (i in 0..3) b[o + i] = (v ushr (8 * i)).toByte()
    }

    private fun isMagic(b: ByteArray, p: Int): Boolean {
        if (p < 0 || p + 8 > b.size) return false
        return b[p].toInt() == 0x64 && b[p + 1].toInt() == 0x65 && b[p + 2].toInt() == 0x78 &&
            b[p + 3].toInt() == 0x0A && (4..6).all { b[p + it].toInt() in 0x30..0x39 } && b[p + 7].toInt() == 0
    }

    /** Posisi awal tiap dex logis (cara yang sama dengan DexReader). Dex biasa = satu posisi (0). */
    fun logicalStarts(b: ByteArray): List<Int> {
        val out = ArrayList<Int>()
        var pos = 0
        while (isMagic(b, pos) && pos + 0x70 <= b.size) {
            out.add(pos)
            val size = le32(b, pos + 0x20)
            if (size < 0x70 || size > b.size - pos) break
            pos += size
            if (!isMagic(b, pos)) {
                val aligned = (pos + 3) and 3.inv()
                if (isMagic(b, aligned)) pos = aligned
            }
        }
        return out
    }

    /**
     * Library smali hanya mau membuka kontainer 041 yang berisi satu dex. Jadi untuk tiap dex
     * logis dibuat salinan buffer: header dex itu dipindah ke awal, lalu ukuran kontainer
     * disamakan dengan ukuran berkas. Semua offset di 041 dihitung dari awal berkas, jadi
     * data dex tersebut tetap terbaca benar.
     */
    private fun dexView(bytes: ByteArray, start: Int, version: String): ByteArray {
        if (version != "041") return bytes
        val copy = bytes.copyOf()
        System.arraycopy(bytes, start, copy, 0, 0x78)
        put32(copy, 0x20, bytes.size) // file_size
        put32(copy, 0x70, bytes.size) // container_size
        put32(copy, 0x74, 0)          // header_offset
        return copy
    }

    // ---------- inti ----------

    fun disassembleBlocking(dexFile: File, outDir: File, log: (String) -> Unit): List<String> {
        val bytes = dexFile.readBytes()
        val version = String(bytes, 4, 3, Charsets.US_ASCII)
        val starts = logicalStarts(bytes)
        if (starts.isEmpty()) throw IllegalArgumentException("${dexFile.name} bukan berkas dex")
        val api = apiFor(version)
        val names = List(starts.size) { "${dexFile.name}-${it + 1}" }
        starts.forEachIndexed { i, s ->
            val dex = DexBackedDexFile(Opcodes.forApi(api), dexView(bytes, s, version))
            val dir = File(outDir, names[i]).apply { mkdirs() }
            log("Membongkar ${names[i]} (${dex.classes.size} class)")
            val opts = BaksmaliOptions()
            opts.apiLevel = api
            if (!Baksmali.disassembleDexFile(dex, dir, threadCount(), opts)) {
                throw IllegalStateException("Gagal membongkar ${names[i]}")
            }
        }
        return names
    }

    private fun assembleBlocking(dir: File, out: File, api: Int) {
        out.parentFile?.mkdirs()
        out.delete()
        val opts = SmaliOptions()
        opts.apiLevel = api
        opts.jobs = threadCount()
        opts.outputDexFile = out.absolutePath

        val oldErr = System.err
        val buf = ByteArrayOutputStream()
        System.setErr(PrintStream(buf, true))
        val ok = try {
            Smali.assemble(opts, listOf(dir.absolutePath))
        } finally {
            System.setErr(oldErr)
        }
        if (!ok) {
            val detail = buf.toString().lineSequence().filter { it.isNotBlank() }.take(6).joinToString("\n")
            throw IllegalStateException("Smali di ${dir.name} ada yang salah:\n$detail")
        }
    }

    // ---------- tingkat proyek ----------

    /** Folder Smali per proyek. Dipilih folder yang bisa dibuka lewat pengelola berkas. */
    fun smaliRoot(ctx: Context, project: String): File =
        File(File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "smali"), project)

    fun hasSmali(ctx: Context, project: String): Boolean = File(smaliRoot(ctx, project), "layout.txt").exists()

    suspend fun disassembleProject(ctx: Context, project: String, log: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val root = smaliRoot(ctx, project)
            root.deleteRecursively()
            root.mkdirs()
            val files = JarAnalyzer.dexFiles(ctx, project)
            if (files.isEmpty()) throw IllegalStateException("JAR ini tidak berisi classes.dex")
            val layout = StringBuilder()
            for (f in files) {
                val version = DexVersion.read(f) ?: throw IllegalStateException("${f.name} bukan berkas dex")
                val names = disassembleBlocking(f, root, log)
                layout.append(f.name).append('|').append(version).append('|').append(names.joinToString(",")).append('\n')
                System.gc()
            }
            File(root, "layout.txt").writeText(layout.toString())
            log("Selesai. Edit file .smali di folder:\n${root.absolutePath}")
        }
    }

    suspend fun assembleProject(ctx: Context, project: String, log: (String) -> Unit): File =
        withContext(Dispatchers.IO) {
            val root = smaliRoot(ctx, project)
            val layoutFile = File(root, "layout.txt")
            if (!layoutFile.exists()) throw IllegalStateException("Bongkar ke Smali dulu")
            val jar = Workspace.jarOf(ctx, project)
            val build = File(root, "_build")
            build.deleteRecursively()
            val outDexDir = File(build, "out").apply { mkdirs() }
            val newDex = ArrayList<File>()

            for (line in layoutFile.readLines().filter { it.isNotBlank() }) {
                val cols = line.split("|")
                val name = cols[0]
                val version = cols[1]
                val folders = cols[2].split(",")
                val container = version == "041"
                val api = if (container) 34 else apiFor(version)

                val parts = folders.map { fn ->
                    val part = File(build, "$fn.dex")
                    log("Merakit $fn")
                    assembleBlocking(File(root, fn), part, api)
                    part
                }
                val out = File(outDexDir, name)
                if (container) {
                    log("Mengemas ${parts.size} dex ke 1 kontainer 041")
                    ContainerPacker.pack(parts, out)
                    verify(parts, out)
                } else {
                    if (parts.size != 1) throw IllegalStateException("$name punya ${parts.size} dex tetapi bukan kontainer 041")
                    parts[0].copyTo(out, overwrite = true)
                }
                parts.forEach { it.delete() }
                newDex.add(out)
            }

            val output = File(Workspace.jarOf(ctx, project).parentFile, "output.jar")
            JarPackager.repack(jar, newDex, output)

            log("Memeriksa hasil (verifikasi)...")
            val checks = JarVerifier.deep(jar, output, File(build, "verify"), JarNames.display(project), "output.jar")
            checks.forEach { log(it.render()) }
            val fails = checks.filter { it.level == VerifyLevel.FAIL }
            build.deleteRecursively()
            if (fails.isNotEmpty()) {
                output.delete()
                throw IllegalStateException(
                    "Verifikasi gagal, output.jar tidak disimpan:\n" + fails.joinToString("\n") { it.text },
                )
            }

            log("Selesai: output.jar (${formatSize(output.length())}). Bisa diekspor lewat menu Ekspor JAR / DEX.")
            output
        }

    /** Cek hasil kontainer: versi, jumlah dex logis, dan daftar class harus sama dengan bahan awalnya. */
    private fun verify(parts: List<File>, packed: File) {
        val expected = parts.flatMap { DexReader.classNames(it) }.sorted()
        val r = DexReader.read(packed)
        val ok = r.version == "041" && r.logical == parts.size && r.classes.sorted() == expected
        if (!ok) {
            throw IllegalStateException(
                "Kontainer hasil tidak cocok (versi ${r.version}, dex ${r.logical}/${parts.size}, " +
                    "class ${r.classes.size}/${expected.size}). JAR tidak dibuat.",
            )
        }
    }
}

