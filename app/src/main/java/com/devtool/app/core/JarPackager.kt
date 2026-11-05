package com.devtool.app.core

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Menyusun ulang JAR: semua entry disalin dari JAR asli dengan urutan, metode kompresi
 * (Stored / Deflated), tanggal, dan komentar yang sama. Hanya isi classes*.dex yang diganti.
 * Jadi classes.dex yang aslinya Stored tetap Stored.
 */
object JarPackager {

    fun repack(original: File, newDex: List<File>, output: File) {
        output.parentFile?.mkdirs()
        output.delete()
        val replacement = newDex.associateBy { it.name }
        val used = HashSet<String>()

        ZipFile(original).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val src = entries.nextElement()
                    val out = ZipEntry(src.name)
                    out.time = src.time
                    if (src.comment != null) out.comment = src.comment

                    if (src.isDirectory) {
                        zos.putNextEntry(out)
                        zos.closeEntry()
                        continue
                    }

                    val repl = replacement[src.name]
                    if (repl != null) {
                        used.add(src.name)
                        writeBytes(zos, out, repl.readBytes(), src.method)
                    } else if (src.method == ZipEntry.STORED) {
                        writeBytes(zos, out, zip.getInputStream(src).use { it.readBytes() }, ZipEntry.STORED)
                    } else {
                        out.method = ZipEntry.DEFLATED
                        zos.putNextEntry(out)
                        zip.getInputStream(src).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                // Dex baru yang tidak ada di JAR asli: ditambahkan di akhir, Stored seperti dex asli
                for ((name, f) in replacement) {
                    if (name in used) continue
                    val out = ZipEntry(name)
                    out.time = original.lastModified()
                    writeBytes(zos, out, f.readBytes(), ZipEntry.STORED)
                }
            }
        }
    }

    private fun writeBytes(zos: ZipOutputStream, entry: ZipEntry, data: ByteArray, method: Int) {
        if (method == ZipEntry.STORED) {
            entry.method = ZipEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = CRC32().also { it.update(data) }.value
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }
}
