package com.jartool.app.core

import java.io.File
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

    /** Salin isi JAR asli, ganti semua classes*.dex dengan dex baru. */
    fun repack(original: File, newDex: List<File>, output: File) {
        ZipFile(original).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (e in zip.entries()) {
                    if (dexName.matches(e.name)) continue
                    out.putNextEntry(ZipEntry(e.name))
                    if (!e.isDirectory) zip.getInputStream(e).use { it.copyTo(out) }
                    out.closeEntry()
                }
                for (f in newDex) {
                    out.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }
}
