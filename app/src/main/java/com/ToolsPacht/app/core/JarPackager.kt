package com.jartool.app.core

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.GregorianCalendar
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Pembuat JAR hasil patch. Tujuannya: hasilnya semirip mungkin dengan JAR asli, karena
 * sistem Android sensitif terhadap hal ini:
 *  - classes*.dex tetap berada di POSISI yang sama dengan aslinya (biasanya paling atas)
 *  - metode simpan dex SAMA dengan aslinya (asli Stored -> hasil Stored, tidak dikompres)
 *  - tanggal/jam tiap berkas disalin dari aslinya (mis. 2008-01-01)
 *  - berkas Stored diratakan ke kelipatan 4 byte (seperti zipalign) supaya dex bisa di-mmap
 */
object JarPackager {
    private val dexName = Regex("classes\\d*\\.dex")
    private val fallbackTime: Long get() = GregorianCalendar(2008, 0, 1, 0, 0, 0).timeInMillis

    /** Ekstrak semua classes*.dex dari JAR ke outDir. */
    fun extractDex(jar: File, outDir: File): List<File> {
        outDir.mkdirs()
        ZipFile(jar).use { zip ->
            return zip.entries().asSequence()
                .filter { dexName.matches(it.name) }
                .sortedBy { it.name }
                .map { e ->
                    File(outDir, e.name).also { f ->
                        zip.getInputStream(e).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    }
                }.toList()
        }
    }

    /** Salin isi JAR asli (urutan, metode simpan, tanggal sama), ganti classes*.dex dengan dex baru. */
    fun repack(original: File, newDex: List<File>, output: File) {
        val fresh = newDex.associateBy { it.name }
        val used = HashSet<String>()
        output.parentFile?.mkdirs()
        ZipFile(original).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val dexEntries = entries.filter { dexName.matches(it.name) }
            val storeNewDex = dexEntries.isEmpty() || dexEntries.all { it.method == ZipEntry.STORED }
            val counter = CountingOutput(output.outputStream().buffered())
            ZipOutputStream(counter).use { out ->
                for (e in entries) {
                    if (dexName.matches(e.name)) {
                        val f = fresh[e.name] ?: continue // dex ini sengaja tidak ada lagi
                        used.add(e.name)
                        writeFile(out, counter, e.name, f, e.method == ZipEntry.STORED, e.time)
                    } else {
                        copyEntry(zip, e, out, counter)
                    }
                }
                val t = dexEntries.firstOrNull()?.time ?: entries.firstOrNull()?.time ?: fallbackTime
                for (f in newDex) {
                    if (f.name !in used) writeFile(out, counter, f.name, f, storeNewDex, t)
                }
            }
        }
    }

    // ---------- bagian dalam ----------

    private class CountingOutput(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private fun newEntry(name: String, time: Long, stored: Boolean, size: Long, crc: Long, counter: CountingOutput): ZipEntry {
        val z = ZipEntry(name)
        z.time = time
        if (stored) {
            z.method = ZipEntry.STORED
            z.size = size
            z.compressedSize = size
            z.crc = crc
            // data berkas dimulai setelah: header lokal (30) + nama + extra. Ratakan ke kelipatan 4.
            val base = counter.count + 30 + name.toByteArray(Charsets.UTF_8).size
            val pad = ((4 - (base % 4)) % 4).toInt()
            if (pad > 0) {
                val extra = ByteArray(4 + pad)
                extra[0] = 0x35; extra[1] = 0xD9.toByte() // id 0xD935 (pola zipalign)
                extra[2] = pad.toByte(); extra[3] = 0
                z.extra = extra
            }
        }
        return z
    }

    private fun copyEntry(zip: ZipFile, e: ZipEntry, out: ZipOutputStream, counter: CountingOutput) {
        val stored = e.method == ZipEntry.STORED
        out.putNextEntry(newEntry(e.name, e.time, stored, e.size, e.crc, counter))
        if (!e.isDirectory) zip.getInputStream(e).use { it.copyTo(out) }
        out.closeEntry()
    }

    private fun writeFile(out: ZipOutputStream, counter: CountingOutput, name: String, f: File, stored: Boolean, time: Long) {
        out.putNextEntry(newEntry(name, time, stored, f.length(), if (stored) crcOf(f) else 0L, counter))
        f.inputStream().buffered().use { it.copyTo(out) }
        out.closeEntry()
    }

    private fun crcOf(f: File): Long {
        val c = CRC32()
        val buf = ByteArray(64 * 1024)
        f.inputStream().use { i ->
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                c.update(buf, 0, n)
            }
        }
        return c.value
    }
}
