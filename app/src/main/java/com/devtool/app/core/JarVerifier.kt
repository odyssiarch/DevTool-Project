package com.devtool.app.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

enum class VerifyLevel { OK, WARN, FAIL }

data class VerifyCheck(val level: VerifyLevel, val text: String) {
    fun render(): String = when (level) {
        VerifyLevel.OK -> "✓ $text"
        VerifyLevel.WARN -> "⚠ $text"
        VerifyLevel.FAIL -> "✗ $text"
    }
}

/**
 * Pemeriksaan JAR hasil patch dibanding JAR asli.
 * Yang dicek: cara simpan classes.dex (Stored / Deflate), urutan, tanggal,
 * berkas lain tidak hilang atau berubah, dan dex hasil masih bisa dibaca.
 * Semua pesan memakai nama JAR yang sebenarnya (nameOld dan nameNew).
 */
object JarVerifier {
    private val dexName = Regex("classes\\d*\\.dex")

    private fun caraSimpan(e: ZipEntry): String =
        if (e.method == ZipEntry.STORED) "tanpa kompres (Stored)" else "dikompres (Deflate)"

    private fun tgl(t: Long): String =
        if (t == -1L) "-" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))

    /** Pemeriksaan cepat: hanya membaca daftar isi JAR. */
    fun structure(
        original: File,
        patched: File,
        nameOld: String = "JAR asli",
        nameNew: String = "JAR hasil",
    ): List<VerifyCheck> {
        val out = ArrayList<VerifyCheck>()
        ZipFile(original).use { za ->
            ZipFile(patched).use { zb ->
                val ea = za.entries().asSequence().toList()
                val eb = zb.entries().asSequence().toList()
                val posA = HashMap<String, Int>()
                ea.forEachIndexed { i, e -> posA[e.name] = i }
                val posB = HashMap<String, Int>()
                eb.forEachIndexed { i, e -> posB[e.name] = i }
                val byB = HashMap<String, ZipEntry>()
                eb.forEach { byB[it.name] = it }

                val dexA = ea.filter { dexName.matches(it.name) }
                if (dexA.isEmpty()) out.add(VerifyCheck(VerifyLevel.WARN, "$nameOld tidak punya classes.dex"))

                for (a in dexA) {
                    val b = byB[a.name]
                    if (b == null) {
                        out.add(VerifyCheck(VerifyLevel.FAIL, "${a.name} hilang dari $nameNew"))
                        continue
                    }
                    if (a.method == b.method) {
                        out.add(
                            VerifyCheck(
                                VerifyLevel.OK,
                                "${a.name}: $nameOld dan $nameNew sama-sama ${caraSimpan(b)}",
                            ),
                        )
                    } else if (a.method == ZipEntry.STORED) {
                        out.add(
                            VerifyCheck(
                                VerifyLevel.FAIL,
                                "${a.name}: $nameOld ${caraSimpan(a)}, tetapi $nameNew ${caraSimpan(b)}. HP bisa gagal booting",
                            ),
                        )
                    } else {
                        out.add(
                            VerifyCheck(
                                VerifyLevel.WARN,
                                "${a.name}: $nameOld ${caraSimpan(a)}, sedangkan $nameNew ${caraSimpan(b)}",
                            ),
                        )
                    }
                    val pa = posA[a.name] ?: 0
                    val pb = posB[a.name] ?: 0
                    if (pa == pb) {
                        out.add(VerifyCheck(VerifyLevel.OK, "${a.name}: urutan sama di kedua JAR (nomor ${pa + 1})"))
                    } else {
                        out.add(
                            VerifyCheck(
                                VerifyLevel.WARN,
                                "${a.name}: urutan beda, $nameOld nomor ${pa + 1}, $nameNew nomor ${pb + 1}",
                            ),
                        )
                    }
                    val sama = a.time == b.time
                    out.add(
                        VerifyCheck(
                            if (sama) VerifyLevel.OK else VerifyLevel.WARN,
                            "${a.name}: tanggal $nameOld ${tgl(a.time)}, $nameNew ${tgl(b.time)}" + if (sama) "" else " (beda)",
                        ),
                    )
                }
                val namesA = ea.map { it.name }.toSet()
                for (b in eb) {
                    if (dexName.matches(b.name) && b.name !in namesA) {
                        out.add(VerifyCheck(VerifyLevel.WARN, "${b.name}: dex baru, tidak ada di $nameOld"))
                    }
                }

                val otherA = ea.filter { !dexName.matches(it.name) }
                val missing = otherA.filter { !byB.containsKey(it.name) }
                val extra = eb.filter { !dexName.matches(it.name) && it.name !in namesA }
                val changed = otherA.filter { a -> byB[a.name]?.let { it.crc != a.crc } == true }
                val methodDiff = otherA.count { a -> !a.isDirectory && byB[a.name]?.let { it.method != a.method } == true }

                if (missing.isNotEmpty()) {
                    out.add(
                        VerifyCheck(
                            VerifyLevel.FAIL,
                            "${missing.size} berkas lain hilang dari $nameNew, contoh: ${missing.first().name}",
                        ),
                    )
                } else {
                    out.add(VerifyCheck(VerifyLevel.OK, "${otherA.size} berkas lain lengkap di $nameNew"))
                }
                if (extra.isNotEmpty()) {
                    out.add(
                        VerifyCheck(
                            VerifyLevel.WARN,
                            "${extra.size} berkas hanya ada di $nameNew, contoh: ${extra.first().name}",
                        ),
                    )
                }
                if (changed.isNotEmpty()) {
                    out.add(
                        VerifyCheck(
                            VerifyLevel.WARN,
                            "${changed.size} berkas lain isinya berbeda, contoh: ${changed.first().name}",
                        ),
                    )
                } else {
                    out.add(VerifyCheck(VerifyLevel.OK, "Isi berkas lain sama di kedua JAR"))
                }
                if (methodDiff > 0) {
                    out.add(VerifyCheck(VerifyLevel.WARN, "$methodDiff berkas lain cara simpannya beda antara $nameOld dan $nameNew"))
                }
            }
        }
        return out
    }

    /** Pemeriksaan lengkap: pemeriksaan cepat ditambah membaca isi dex hasil. */
    fun deep(
        original: File,
        patched: File,
        work: File,
        nameOld: String = "JAR asli",
        nameNew: String = "JAR hasil",
    ): List<VerifyCheck> {
        val out = ArrayList<VerifyCheck>(structure(original, patched, nameOld, nameNew))
        try {
            val da = JarPackager.extractDex(original, File(work, "asli"))
            val db = JarPackager.extractDex(patched, File(work, "hasil"))
            if (db.isEmpty()) out.add(VerifyCheck(VerifyLevel.FAIL, "$nameNew tidak punya classes.dex"))
            for (b in db) {
                val rb = DexReader.read(b)
                val jumlahB = rb.classes.size
                if (jumlahB == 0) {
                    out.add(VerifyCheck(VerifyLevel.FAIL, "${b.name}: di $nameNew tidak berisi class sama sekali"))
                    continue
                }
                out.add(
                    VerifyCheck(
                        VerifyLevel.OK,
                        "${b.name}: $nameNew bisa dibaca (versi ${rb.version}, ${rb.logical} dex, $jumlahB class)",
                    ),
                )
                val a = da.firstOrNull { it.name == b.name } ?: continue
                val ra = DexReader.read(a)
                if (ra.version != rb.version) {
                    out.add(VerifyCheck(VerifyLevel.WARN, "${b.name}: versi dex beda, $nameOld ${ra.version}, $nameNew ${rb.version}"))
                }
                if (ra.logical != rb.logical) {
                    out.add(VerifyCheck(VerifyLevel.WARN, "${b.name}: jumlah dex beda, $nameOld ${ra.logical}, $nameNew ${rb.logical}"))
                }
                val jumlahA = ra.classes.size
                if (jumlahA == jumlahB) {
                    out.add(VerifyCheck(VerifyLevel.OK, "${b.name}: jumlah class sama di kedua JAR ($jumlahA)"))
                } else {
                    out.add(VerifyCheck(VerifyLevel.WARN, "${b.name}: jumlah class beda, $nameOld $jumlahA, $nameNew $jumlahB"))
                }
            }
        } catch (e: Exception) {
            out.add(VerifyCheck(VerifyLevel.FAIL, "Dex $nameNew gagal dibaca: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            File(work, "asli").deleteRecursively()
            File(work, "hasil").deleteRecursively()
        }
        return out
    }
}
