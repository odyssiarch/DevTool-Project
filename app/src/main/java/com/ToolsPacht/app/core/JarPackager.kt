package com.jartool.app.core

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object JarPackager {
    private val dexName = Regex("classes\\d*\\.dex")

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

    private fun crcOf(f: File): Long {
        val c = CRC32()
        val buf = ByteArray(64 * 1024)
        f.inputStream().buffered().use { i ->
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                c.update(buf, 0, n)
            }
        }
        return c.value
    }

    /** Tulis satu dex. Cara simpannya (Stored / Deflate) dan tanggalnya mengikuti JAR asli. */
    private fun putDex(out: ZipOutputStream, name: String, f: File, stored: Boolean, time: Long) {
        val ze = ZipEntry(name)
        if (stored) {
            ze.method = ZipEntry.STORED
            ze.size = f.length()
            ze.compressedSize = f.length()
            ze.crc = crcOf(f)
        }
        if (time != -1L) ze.time = time
        out.putNextEntry(ze)
        f.inputStream().use { it.copyTo(out) }
        out.closeEntry()
    }

    /**
     * Salin isi JAR asli, ganti classes*.dex dengan dex baru.
     * Yang dijaga sama dengan aslinya: urutan berkas, cara simpan (Stored/Deflate), dan tanggal.
     * Dex baru ditaruh di posisi dex lama.
     */
    fun repack(original: File, newDex: List<File>, output: File) {
        val byName = newDex.associateBy { it.name }
        val written = HashSet<String>()
        ZipFile(original).use { zip ->
            val firstDex = zip.entries().asSequence().firstOrNull { dexName.matches(it.name) }
            val dexTime = firstDex?.time ?: -1L
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (e in zip.entries()) {
                    if (dexName.matches(e.name)) {
                        val f = byName[e.name] ?: continue
                        putDex(out, e.name, f, e.method == ZipEntry.STORED, e.time)
                        written.add(e.name)
                        continue
                    }
                    val ze = ZipEntry(e.name)
                    if (e.method == ZipEntry.STORED) {
                        val ukuran = if (e.isDirectory) 0L else e.size
                        ze.method = ZipEntry.STORED
                        ze.size = ukuran
                        ze.compressedSize = ukuran
                        ze.crc = if (e.isDirectory) 0L else e.crc
                    }
                    if (e.time != -1L) ze.time = e.time
                    out.putNextEntry(ze)
                    if (!e.isDirectory) zip.getInputStream(e).use { it.copyTo(out) }
                    out.closeEntry()
                }
                // dex yang tidak ada di JAR asli: simpan tanpa kompres
                for (f in newDex) {
                    if (f.name in written) continue
                    putDex(out, f.name, f, true, dexTime)
                }
            }
        }
    }
}
