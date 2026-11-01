package com.jartool.app.core

import java.io.File
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Pemeriksa hasil Rebuild: membandingkan output.jar dengan JAR asli. */
object JarVerifier {
    private val dexName = Regex("classes\\d*\\.dex")

    class Item(val ok: Boolean, val text: String)
    class Report(val items: List<Item>) {
        val ok: Boolean get() = items.all { it.ok }
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun methodName(m: Int) = if (m == ZipEntry.STORED) "Stored (tanpa kompres)" else "Deflated (dikompres)"

    fun verify(original: File, output: File): Report {
        val items = ArrayList<Item>()
        try {
            ZipFile(original).use { a ->
                ZipFile(output).use { b ->
                    val ea = a.entries().asSequence().toList()
                    val eb = b.entries().asSequence().toList()
                    val na = ea.map { it.name }
                    val nb = eb.map { it.name }
                    val mb = eb.associateBy { it.name }

                    // 1. daftar dan urutan berkas
                    if (na == nb) {
                        items += Item(true, "Daftar dan urutan ${na.size} berkas sama dengan asli")
                    } else {
                        val i = na.indices.firstOrNull { it >= nb.size || na[it] != nb[it] } ?: na.size
                        items += Item(
                            false,
                            "Daftar/urutan berkas beda (asli ${na.size}, hasil ${nb.size}); mulai beda di nomor ${i + 1}: " +
                                "asli ${na.getOrNull(i)} / hasil ${nb.getOrNull(i)}",
                        )
                    }

                    // 2. isi berkas selain dex tidak boleh berubah
                    val others = ea.filter { !dexName.matches(it.name) }
                    val diff = others.filter { mb[it.name]?.crc != it.crc }
                    items += if (diff.isEmpty()) Item(true, "Isi ${others.size} berkas non-dex tidak berubah")
                    else Item(false, "${diff.size} berkas non-dex isinya beda, mis. ${diff.take(3).joinToString { it.name }}")

                    // 3. tanggal
                    val badTime = ea.filter { mb[it.name]?.time != it.time }
                    items += if (badTime.isEmpty()) Item(true, "Tanggal semua berkas sama dengan asli")
                    else Item(false, "${badTime.size} berkas tanggalnya beda, mis. ${badTime.take(3).joinToString { it.name }}")

                    // 4. tiap dex: metode simpan, posisi, versi, jumlah class, checksum
                    val dexA = ea.filter { dexName.matches(it.name) }
                    if (dexA.isEmpty()) items += Item(false, "JAR asli tidak berisi classes.dex")
                    for (d in dexA) {
                        val n = mb[d.name]
                        if (n == null) {
                            items += Item(false, "${d.name} tidak ada di hasil")
                            continue
                        }
                        val posA = na.indexOf(d.name) + 1
                        val posB = nb.indexOf(d.name) + 1
                        val okMethod = n.method == d.method
                        val okPos = posA == posB
                        items += Item(
                            okMethod && okPos,
                            "${d.name}: ${methodName(n.method)}" + (if (okMethod) "" else " (asli ${methodName(d.method)})") +
                                ", urutan ke-$posB" + (if (okPos) "" else " (asli ke-$posA)"),
                        )

                        val ba = a.getInputStream(d).use { it.readBytes() }
                        val bb = b.getInputStream(n).use { it.readBytes() }
                        val sa = SmaliEngine.logicalStarts(ba)
                        val sb = SmaliEngine.logicalStarts(bb)
                        if (sb.isEmpty()) {
                            items += Item(false, "${d.name} hasil tidak terbaca sebagai dex")
                            continue
                        }
                        val va = String(ba, 4, 3, Charsets.US_ASCII)
                        val vb = String(bb, 4, 3, Charsets.US_ASCII)
                        val ca = sa.sumOf { le32(ba, it + 0x60) }
                        val cb = sb.sumOf { le32(bb, it + 0x60) }
                        items += Item(
                            va == vb && sa.size == sb.size,
                            "${d.name}: dex $vb, ${sb.size} dex logis, $cb class" +
                                (if (va == vb && sa.size == sb.size) "" else " (asli dex $va, ${sa.size} dex logis)") +
                                (if (ca == cb) "" else " [asli $ca class]"),
                        )

                        if (vb != "041" && sb.size == 1 && le32(bb, 0x20) == bb.size) {
                            val ad = Adler32().also { it.update(bb, 12, bb.size - 12) }
                            val sha = MessageDigest.getInstance("SHA-1").also { it.update(bb, 32, bb.size - 32) }.digest()
                            val okSum = ad.value.toInt() == le32(bb, 8) && sha.contentEquals(bb.copyOfRange(12, 32))
                            items += Item(okSum, "${d.name}: checksum dan signature dex ${if (okSum) "benar" else "SALAH"}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            items += Item(false, "Verifikasi gagal dijalankan: ${e.message}")
        }
        return Report(items)
    }
}
